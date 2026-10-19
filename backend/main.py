
import os
import uuid
from typing import Literal
from uuid import UUID

from fastapi import FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker

from models import User, Transaction, SyncedBluetoothTransaction


# Connect to the PostgreSQL database
DATABASE_URL = os.getenv("DATABASE_URL")

if not DATABASE_URL:
    raise RuntimeError(
        "Please configure the DATABASE_URL environment variable."
    )

engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True
)

SessionLocal = sessionmaker(
    bind=engine,
    autoflush=False,
    expire_on_commit=False
)


# Create the FastAPI app
app = FastAPI(title="TETHR API")

# Serve the existing frontend files
app.mount(
    "/static",
    StaticFiles(directory="static"),
    name="static"
)


# Details entered while registering a customer or merchant
class RegisterRequest(BaseModel):
    user_id: str | None = None
    role: Literal["customer", "merchant"]
    name: str = Field(min_length=1, max_length=100)
    phone_number: str | None = Field(default=None, max_length=20)


# Details needed for a simulated payment
class PaymentRequest(BaseModel):
    customer_id: str
    merchant_id: str
    amount: int = Field(gt=0, le=999)
    description: str = Field(
        default="Payment",
        min_length=1,
        max_length=200
    )


# Details sent from Android when syncing Bluetooth transactions
class SyncRecordRequest(BaseModel):
    requestId: UUID
    amountPaise: int = Field(gt=0)
    type: Literal["DEBIT", "CREDIT"]
    status: Literal["COMPLETED"]
    timestamp: int = Field(gt=0)


class SyncTransactionsRequest(BaseModel):
    records: list[SyncRecordRequest] = Field(
        min_length=1,
        max_length=100
    )


class SyncRecordResult(BaseModel):
    requestId: UUID
    type: Literal["DEBIT", "CREDIT"]
    status: Literal["synced", "already_synced", "conflict"]


class SyncTransactionsResponse(BaseModel):
    results: list[SyncRecordResult]


# Check whether the backend is running
@app.get("/")
def home():
    return {"message": "TETHR API is running"}


@app.get("/health")
def health():
    return {"status": "ok"}


# Open the existing web page
@app.get("/app")
def frontend():
    return FileResponse("static/index.html")


# Register a new customer or merchant
@app.post("/register")
def register(data: RegisterRequest):

    # Remove extra spaces from the name
    name = data.name.strip()

    if not name:
        raise HTTPException(
            status_code=400,
            detail="Please enter your name."
        )

    # Phone number is optional
    phone_number = (
        data.phone_number.strip()
        if data.phone_number
        else None
    )

    if phone_number == "":
        phone_number = None

    # Create an ID if Android has not provided one
    user_id = (
        data.user_id.strip()
        if data.user_id
        else str(uuid.uuid4())
    )

    if not user_id:
        user_id = str(uuid.uuid4())

    with SessionLocal.begin() as db:

        # Check if this user ID is already registered
        existing_user = db.get(User, user_id)

        if existing_user:
            raise HTTPException(
                status_code=409,
                detail="This user ID already exists."
            )

        # Customers get a demo balance; merchants start at zero
        balance = 1000 if data.role == "customer" else 0

        # Save the user's details in PostgreSQL
        user = User(
            user_id=user_id,
            role=data.role,
            balance=balance,
            name=name,
            phone_number=phone_number
        )

        db.add(user)
        db.flush()

        # Send the new profile details back to Android
        result = {
            "message": "Registration successful",
            "user_id": user.user_id,
            "name": user.name,
            "phone_number": user.phone_number,
            "role": user.role,
            "balance": user.balance
        }

    return result


# Get a customer's or merchant's profile
@app.get("/users/{user_id}")
def get_user_profile(user_id: str):
    with SessionLocal() as db:
        user = db.get(User, user_id)

        if user is None:
            raise HTTPException(
                status_code=404,
                detail="User not found."
            )

        return {
            "user_id": user.user_id,
            "name": user.name,
            "phone_number": user.phone_number,
            "role": user.role,
            "balance": user.balance
        }


# Get the current demo wallet balance
@app.get("/balance")
def get_balance(user_id: str):
    with SessionLocal() as db:
        user = db.get(User, user_id)

        if user is None:
            raise HTTPException(
                status_code=404,
                detail="User not found."
            )

        return {
            "user_id": user.user_id,
            "name": user.name,
            "role": user.role,
            "balance": user.balance
        }


# Make a simulated payment from a customer to a merchant
@app.post("/pay")
def pay(data: PaymentRequest):

    if data.customer_id == data.merchant_id:
        raise HTTPException(
            status_code=400,
            detail="You cannot pay yourself."
        )

    with SessionLocal.begin() as db:

        # Find the customer and lock the row while updating the balance
        customer = db.execute(
            select(User)
            .where(User.user_id == data.customer_id)
            .with_for_update()
        ).scalar_one_or_none()

        # Find the merchant
        merchant = db.execute(
            select(User)
            .where(User.user_id == data.merchant_id)
            .with_for_update()
        ).scalar_one_or_none()

        if customer is None or merchant is None:
            raise HTTPException(
                status_code=404,
                detail="Customer or merchant not found."
            )

        if customer.role != "customer":
            raise HTTPException(
                status_code=400,
                detail="The payer must be a customer."
            )

        if merchant.role != "merchant":
            raise HTTPException(
                status_code=400,
                detail="The receiver must be a merchant."
            )

        # Keep the existing demo payment limits
        if data.amount > 999:
            raise HTTPException(
                status_code=400,
                detail="Maximum transaction limit is ₹999."
            )

        if customer.balance - data.amount < 50:
            raise HTTPException(
                status_code=400,
                detail="A minimum balance of ₹50 must remain."
            )

        # Update the simulated wallet balances
        customer.balance -= data.amount
        merchant.balance += data.amount

        transaction_id = str(uuid.uuid4())

        # Save the transaction with both user IDs
        transaction = Transaction(
            transaction_id=transaction_id,
            customer_id=customer.user_id,
            merchant_id=merchant.user_id,
            amount=data.amount,
            description=data.description
        )

        db.add(transaction)
        db.flush()

        result = {
            "message": "Payment successful (SIMULATED)",
            "transaction_id": transaction_id,
            "customer_id": customer.user_id,
            "customer_name": customer.name,
            "merchant_id": merchant.user_id,
            "merchant_name": merchant.name,
            "amount": data.amount,
            "description": data.description,
            "customer_balance": customer.balance,
            "merchant_balance": merchant.balance
        }

    return result


# Prepare a transaction with customer and merchant details
def transaction_to_dict(transaction, db):

    customer = db.get(User, transaction.customer_id)
    merchant = db.get(User, transaction.merchant_id)

    return {
        "transaction_id": transaction.transaction_id,

        "customer_id": transaction.customer_id,
        "customer_name": customer.name if customer else None,
        "customer_phone": (
            customer.phone_number if customer else None
        ),

        "merchant_id": transaction.merchant_id,
        "merchant_name": merchant.name if merchant else None,
        "merchant_phone": (
            merchant.phone_number if merchant else None
        ),

        "amount": transaction.amount,
        "description": transaction.description
    }


# Check whether a transaction matches an existing synced record
def synced_record_matches(existing, record):
    return (
        existing.amount_paise == record.amountPaise
        and existing.status == record.status
        and existing.occurred_at_ms == record.timestamp
    )


# Save Bluetooth transactions received from the Android app
@app.post(
    "/sync/transactions",
    response_model=SyncTransactionsResponse
)
def sync_transactions(data: SyncTransactionsRequest):

    results = []

    with SessionLocal.begin() as db:

        for record in data.records:

            # Check whether this transaction was synced earlier
            existing = db.execute(
                select(SyncedBluetoothTransaction).where(
                    SyncedBluetoothTransaction.request_id
                    == record.requestId,
                    SyncedBluetoothTransaction.record_type
                    == record.type
                )
            ).scalar_one_or_none()

            if existing is not None:

                if synced_record_matches(existing, record):
                    result_status = "already_synced"
                else:
                    result_status = "conflict"

                results.append(
                    SyncRecordResult(
                        requestId=record.requestId,
                        type=record.type,
                        status=result_status
                    )
                )
                continue

            # Create a new synced transaction
            synced_transaction = SyncedBluetoothTransaction(
                request_id=record.requestId,
                record_type=record.type,
                amount_paise=record.amountPaise,
                status=record.status,
                occurred_at_ms=record.timestamp
            )

            try:
                # A savepoint lets the rest of the batch continue
                # if another request inserts the same transaction.
                with db.begin_nested():
                    db.add(synced_transaction)
                    db.flush()

            except IntegrityError:
                existing = db.execute(
                    select(SyncedBluetoothTransaction).where(
                        SyncedBluetoothTransaction.request_id
                        == record.requestId,
                        SyncedBluetoothTransaction.record_type
                        == record.type
                    )
                ).scalar_one_or_none()

                if existing is None:
                    raise

                if synced_record_matches(existing, record):
                    result_status = "already_synced"
                else:
                    result_status = "conflict"

            else:
                result_status = "synced"

            results.append(
                SyncRecordResult(
                    requestId=record.requestId,
                    type=record.type,
                    status=result_status
                )
            )

    return SyncTransactionsResponse(results=results)


# Get all simulated backend payments
@app.get("/transactions")
def get_transactions():
    with SessionLocal() as db:

        transactions = db.scalars(
            select(Transaction).order_by(
                Transaction.transaction_id
            )
        ).all()

        return [
            transaction_to_dict(transaction, db)
            for transaction in transactions
        ]


# Get transactions belonging to a customer or merchant
@app.get("/transactions/{user_id}")
def get_user_transactions(user_id: str):
    with SessionLocal() as db:

        user = db.get(User, user_id)

        if user is None:
            raise HTTPException(
                status_code=404,
                detail="User not found."
            )

        transactions = db.scalars(
            select(Transaction).where(
                (Transaction.customer_id == user_id)
                | (Transaction.merchant_id == user_id)
            )
        ).all()

        return [
            transaction_to_dict(transaction, db)
            for transaction in transactions
        ]


# Get a receipt for one simulated payment
@app.get("/receipt/{transaction_id}")
def get_receipt(transaction_id: str):
    with SessionLocal() as db:

        transaction = db.get(Transaction, transaction_id)

        if transaction is None:
            raise HTTPException(
                status_code=404,
                detail="Transaction not found."
            )

        return {
            "receipt": "Payment Successful (SIMULATED)",
            **transaction_to_dict(transaction, db)
        }

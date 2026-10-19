
from sqlalchemy import (
    BigInteger,
    CheckConstraint,
    Column,
    DateTime,
    ForeignKey,
    Identity,
    Integer,
    String,
    UniqueConstraint,
    func,
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import declarative_base


Base = declarative_base()


class User(Base):
    __tablename__ = "users"

    user_id = Column(String, primary_key=True)
    role = Column(String, nullable=False)
    balance = Column(Integer, nullable=False)

    # Profile details
    name = Column(String(100), nullable=True)
    phone_number = Column(String(20), nullable=True)


class Transaction(Base):
    __tablename__ = "transactions"

    transaction_id = Column(String, primary_key=True)
    customer_id = Column(String, nullable=False)
    merchant_id = Column(String, nullable=False)
    amount = Column(Integer, nullable=False)
    description = Column(
        String,
        nullable=False,
        default="Payment",
    )


class SyncedBluetoothTransaction(Base):
    __tablename__ = "synced_bluetooth_transactions"

    id = Column(BigInteger, Identity(), primary_key=True)
    request_id = Column(UUID(as_uuid=True), nullable=False)
    record_type = Column(String(6), nullable=False)
    amount_paise = Column(BigInteger, nullable=False)
    status = Column(String(16), nullable=False)
    occurred_at_ms = Column(BigInteger, nullable=False)
    received_at = Column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

    # Optional identity references for backward compatibility
    customer_id = Column(String, nullable=True)
    merchant_id = Column(String, nullable=True)

    __table_args__ = (
        UniqueConstraint(
            "request_id",
            "record_type",
            name="uq_synced_bluetooth_transactions_request_type",
        ),
        CheckConstraint(
            "record_type IN ('DEBIT', 'CREDIT')",
            name="ck_synced_bluetooth_transactions_record_type",
        ),
        CheckConstraint(
            "status = 'COMPLETED'",
            name="ck_synced_bluetooth_transactions_status",
        ),
        CheckConstraint(
            "amount_paise > 0",
            name="ck_synced_bluetooth_transactions_amount_positive",
        ),
        CheckConstraint(
            "occurred_at_ms > 0",
            name="ck_synced_bluetooth_transactions_timestamp_positive",
        ),
    )

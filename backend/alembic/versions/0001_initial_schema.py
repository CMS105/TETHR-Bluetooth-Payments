"""Create the initial BluetoothUPI demo schema.

Revision ID: 0001_initial_schema
Revises: None
Create Date: 2026-10-19

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "0001_initial_schema"
down_revision: Union[str, Sequence[str], None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Create the users and transactions tables."""
    op.create_table(
        "users",
        sa.Column("user_id", sa.String(), nullable=False),
        sa.Column("role", sa.String(), nullable=False),
        sa.Column("balance", sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint("user_id"),
    )

    op.create_table(
        "transactions",
        sa.Column("transaction_id", sa.String(), nullable=False),
        sa.Column("customer_id", sa.String(), nullable=False),
        sa.Column("merchant_id", sa.String(), nullable=False),
        sa.Column("amount", sa.Integer(), nullable=False),
        # The "Payment" default stays in the SQLAlchemy application model.
        sa.Column("description", sa.String(), nullable=False),
        sa.PrimaryKeyConstraint("transaction_id"),
    )


def downgrade() -> None:
    """Remove the tables in reverse creation order."""
    op.drop_table("transactions")
    op.drop_table("users")

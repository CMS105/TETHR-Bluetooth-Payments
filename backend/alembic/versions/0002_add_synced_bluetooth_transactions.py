"""Add storage for already-completed Bluetooth transaction records.

Revision ID: 0002_sync_transactions
Revises: 0001_initial_schema
Create Date: 2026-10-19

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


# revision identifiers, used by Alembic.
revision: str = "0002_sync_transactions"
down_revision: Union[str, Sequence[str], None] = "0001_initial_schema"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Create storage for client-reported Bluetooth transaction records."""
    op.create_table(
        "synced_bluetooth_transactions",
        sa.Column(
            "id",
            sa.BigInteger(),
            sa.Identity(),
            nullable=False,
        ),
        sa.Column(
            "request_id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
        ),
        sa.Column("record_type", sa.String(length=6), nullable=False),
        sa.Column("amount_paise", sa.BigInteger(), nullable=False),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("occurred_at_ms", sa.BigInteger(), nullable=False),
        sa.Column(
            "received_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "record_type IN ('DEBIT', 'CREDIT')",
            name="ck_synced_bluetooth_transactions_record_type",
        ),
        sa.CheckConstraint(
            "status = 'COMPLETED'",
            name="ck_synced_bluetooth_transactions_status",
        ),
        sa.CheckConstraint(
            "amount_paise > 0",
            name="ck_synced_bluetooth_transactions_amount_positive",
        ),
        sa.CheckConstraint(
            "occurred_at_ms > 0",
            name="ck_synced_bluetooth_transactions_timestamp_positive",
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "request_id",
            "record_type",
            name="uq_synced_bluetooth_transactions_request_type",
        ),
    )


def downgrade() -> None:
    """Remove only the Bluetooth transaction sync table."""
    op.drop_table("synced_bluetooth_transactions")

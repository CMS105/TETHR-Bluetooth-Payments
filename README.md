# TETHR — Bluetooth Payments

**An Android-based offline payment demo using Bluetooth, with backend transaction synchronization.**

TETHR explores a Bluetooth-based demo payment workflow on Android and stores transaction records locally. The backend provides profile/payment demo endpoints and accepts transaction records for synchronization when network connectivity is available.

> **Important:** This is an educational prototype. It does not transfer real money and is not a production UPI application. Wallet balances and payment endpoints are simulated.

## Features

- Android application built with Kotlin and Jetpack Compose
- Bluetooth Classic communication for a local demo payment request/response
- Local transaction storage using Room
- FastAPI backend for profile operations and transaction synchronization
- PostgreSQL storage for backend records
- Transaction status tracking and duplicate-sync handling

## Tech Stack

| Component | Technology |
|---|---|
| Android | Kotlin, Jetpack Compose |
| Local storage | Room |
| Backend API | Python, FastAPI |
| ORM and migrations | SQLAlchemy, Alembic |
| Database | PostgreSQL |
| Version control | Git, GitHub |

## Repository structure

```text
TETHR-Bluetooth-Payments/
├── android/    # Android application
├── backend/    # FastAPI backend and database migrations
├── .gitignore
└── README.md
```

## Requirements

- Android Studio with Android SDK Platform 36 and Java/JDK 17 or newer
- Python 3.10+ (use a version compatible with the pinned dependencies)
- PostgreSQL database for the backend
- Two physical Android phones for the Bluetooth Classic/RFCOMM demonstration
- Bluetooth enabled on both phones

## Run the Android app

1. Open the `android/` folder in Android Studio.
2. Allow Gradle sync to finish.
3. Select the app run configuration and run it on a compatible Android device.
4. For the Bluetooth demonstration, pair two physical phones through Android Settings first. Start the Customer listener on one phone and connect from the Merchant phone.
5. Send a demo request and approve or reject it on the Customer phone.

The Bluetooth request/response flow is a demo; it does not send a real bank or UPI payment. See [Android-specific instructions](android/README.md) for more details.

## Run the backend (Windows PowerShell)

Run these commands from the `backend/` directory:

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
$env:DATABASE_URL = "postgresql+psycopg://USER:PASSWORD@HOST:5432/DATABASE_NAME"
python -m uvicorn main:app --host 127.0.0.1 --port 8001
```

Replace the example database URL with your own PostgreSQL connection string. Do not commit actual credentials. The backend reads `DATABASE_URL` from the environment. The commands above set it for the current PowerShell session only.

- Health check: `http://127.0.0.1:8001/health`
- Interactive API docs: `http://127.0.0.1:8001/docs`
- Android emulator access to the Windows host: `http://10.0.2.2:8001/` (the app's configured development endpoint may depend on the Android build configuration)

Database schema migrations may be needed when setting up a fresh database. Review the Alembic migration files before running migrations against any database.

## Demo verification checklist

- [ ] Android app opens successfully.
- [ ] Two physical phones pair and connect over Bluetooth.
- [ ] A demo payment request can be approved and rejected.
- [ ] Transactions are stored locally on the Android device.
- [ ] Backend health endpoint responds successfully.
- [ ] A pending transaction syncs when the backend is available.
- [ ] Repeating a sync does not create a duplicate backend record.
- [ ] Backend-unavailable behavior is checked and pending transactions remain available for retry.

Only mark items after testing them on your own setup.

## Security and limitations

- Never commit `.env` files, database credentials, access tokens, signing keys, or local database files.
- Keep real secrets in environment variables or a local secret manager.
- This repository is a development prototype; it has not been represented as production-ready or as a regulated payment application.
- The Android emulator URL `10.0.2.2` and local HTTP settings are for development. Do not use this configuration for a public production deployment.

## Project status

Educational prototype under development.

## Author

**M. S. Chirag**

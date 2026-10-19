# TETHR — Bluetooth Payments

**An Android-based offline payment demo using Bluetooth, with backend transaction synchronization.**

TETHR explores how two devices can exchange demo payment transactions locally and synchronize transaction records with a backend when connectivity is available.

> **Disclaimer:** This is an educational prototype. It does not transfer real money and is not a production UPI application.

## Features

- Bluetooth-based payment workflow prototype
- Android application built with Kotlin and Jetpack Compose
- Local transaction and wallet data using Room
- FastAPI backend for transaction synchronization
- PostgreSQL database for backend records
- Transaction status and balance tracking
- Duplicate transaction prevention during synchronization

## Tech Stack

| Component | Technology |
|---|---|
| Android | Kotlin, Jetpack Compose |
| Local storage | Room |
| Backend API | Python, FastAPI |
| Database | PostgreSQL |
| Version control | Git, GitHub |

## Project Structure

```text
TETHR-Bluetooth-Payments/
├── android/    # Android application
├── backend/    # FastAPI backend
└── README.md   # Project documentation
```

## Getting Started

### Android
1. Open the `android/` folder in Android Studio.
2. Allow Gradle to sync.
3. Configure the backend base URL for your emulator or device.
4. Build and run the application.

### Backend
1. Open the `backend/` folder.
2. Create and activate a Python virtual environment.
3. Install dependencies from `requirements.txt`.
4. Configure the required database connection using environment variables.
5. Start the FastAPI server using the project's entry point.

Check the source code and configuration for any additional setup requirements.

## Security Note

Do not commit API keys, database credentials, `.env` files, signing keys, or other secrets to the repository.

## Project Status

Educational prototype under development.

## Author

**M. S. Chirag**


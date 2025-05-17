# Bibliobant - Book Sharing App

Bibliobant is an Android app for swapping, lending, and selling books. It uses Firebase for OTP authentication and Firestore for storing user and book data, along with Google Maps for location-based features.

## Prerequisites
Before you start, ensure you have the following:
- **Android Studio**: Version Arctic Fox (2020.3.1) or later.
- **Java**: Version 11 or higher.
- **A Firebase Project**: You’ll need to set up your own Firebase project.
- **A Real Device**: Emulators might not work well with Firebase App Check (Play Integrity).

## Setup Instructions
Follow these steps to get the app running on your machine:

1. **Clone the Repo**:
    - Since this repo is private, you need to be a collaborator. Contact `elanchezhiyan2005` to be added.
    - Clone the repo to your laptop:
      ```
      git clone https://github.com/elanchezhiyan2005/book-swap-app.git
      ```
    - Authenticate with your GitHub username and a personal access token (not your password). To generate a token:
        - Go to GitHub > Settings > Developer settings > Personal access tokens > Tokens (classic) > Generate new token.
        - Give it `repo` permissions and use it when prompted.

2. **Set Up Firebase**:
    - Create a Firebase project at [Firebase Console](https://console.firebase.google.com/).
    - Add an Android app with the package name `com.example.bookswapapp3`.
    - Download the `google-services.json` file and place it in the `app/` folder of the project.
    - Enable **Phone Authentication** in the Authentication section.
    - Enable **Firestore** in the Firestore Database section.
    - (Optional) Add test phone numbers in the Authentication section to simulate OTP without sending real SMS (saves quota).

3. **Add a Google Maps API Key**:
    - The app uses Google Maps for location features (e.g., in `AddNewBookActivity`).
    - Get an API key from [Google Cloud Console](https://console.cloud.google.com/):
        - Create a project, enable the Maps SDK for Android, and generate an API key.
    - Open `app/src/main/AndroidManifest.xml` and replace the placeholder with your key:
      ```xml
      <meta-data
          android:name="com.google.android.geo.API_KEY"
          android:value="YOUR_API_KEY_HERE"/>
      ```

4. **Set Up Firebase App Check**:
    - In the Firebase Console, go to **App Check** and enable it for your app.
    - For development, the app uses `DebugAppCheckProvider` (set in `BibliobantApplication.java` with `IS_DEBUG_MODE = true`).
    - Run the app once, then check Logcat in Android Studio for a debug token (e.g., `DebugAppCheckProvider: Enter this debug token...`).
    - Whitelist the debug token in the Firebase Console under App Check > Debug provider.
    - Before production, switch to `PlayIntegrityAppCheckProvider` by setting `IS_DEBUG_MODE = false` in `BibliobantApplication.java`.

5. **Open the Project in Android Studio**:
    - Open Android Studio and select **Open an existing project**.
    - Navigate to the cloned `book-swap-app` folder and open it.
    - Let Gradle sync (this might take a minute). If prompted about `local.properties`, let Android Studio generate it (it sets your SDK path).

6. **Run the App**:
    - Connect a real Android device via USB (emulators might not work well with Play Integrity).
    - Build and run the app (`Shift + F10` in Android Studio).
    - The app starts with `SendOtpActivity`. Use a test phone number (from Firebase Console) or a real number to receive an OTP, verify it in `VerifyOtpActivity`, and proceed to `HomeActivity`.

## App Flow
Here’s how the app works:
- **SendOtpActivity**: Enter your phone number to receive an OTP via SMS.
- **VerifyOtpActivity**: Enter the OTP to verify your number, then proceed to the home screen.
- **HomeActivity**: Access features like adding a new book (`AddNewBookActivity`), viewing books (`ViewBooksActivity`), and messaging (`MessagesActivity`).

## Dependencies
The app relies on the following:
- **Firebase Authentication**: For phone number OTP authentication.
- **Firebase Firestore**: For storing user and book data.
- **Firebase App Check**: For securing Firebase requests (Debug mode for development, Play Integrity for production).
- **Google Maps API**: For location-based features.
- Check `app/build.gradle` for the full list of dependencies.

## Notes
- **Test Phone Numbers**: Use Firebase test phone numbers during development to avoid sending real SMS and hitting quotas (e.g., 10 SMS/day on the free plan).
- **App Check Logs**: If Firebase requests fail, check Logcat for App Check errors and ensure your debug token is whitelisted.
- **Location Permissions**: The app requests location permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) for Google Maps features.
- If you run into issues, contact `elanchezhiyan2005` or open an issue in this repo.

## Contributing
- Feel free to create branches and submit pull requests for new features or bug fixes.
- Let’s collaborate to make Bibliobant even better!

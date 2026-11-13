# Fix wave report

1. LoopbackOAuthBrowser: new `errorPage` param (defaults to successPage); `error=` redirects get it. New string `google_drive_signin_incomplete`, HostSyncStrings.googleDriveSignInIncomplete, desktop binding, factory, fixtures. Test added.
2. PkceGoogleAuth: a returned `scope` lacking drive.appdata makes signIn return false and store nothing; absent scope unchanged. Two tests.
3. Desktop and iOS bindings keep the injected engine and close it with the client. Desktop test added (engine job inactive after Koin close); iOS compile-only.

Gates: :sharedLogic:jvmTest 1312 tests, 0 failures; iOS arm64, simulator and simulator test compiles green; :sharedUi:compileKotlinJvm green.

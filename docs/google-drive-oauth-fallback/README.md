Static fallback page for the Android Google Drive browser sign-in redirect; it has no scripts, no analytics and no external resources.
Serve `index.html` at `https://read.andbible.org/oauth2/drive` (and `/oauth2/drive/`), without logging or forwarding the query string.

# Y2Akio Mobile User Guide

This guide explains how to use Y2Akio Mobile to analyze links, download media, manage galleries and playlists, and troubleshoot common problems.

> Download only content that you own or have permission to use. The availability of a download does not grant permission to copy or redistribute it.

## 1. Requirements

- Android 14 or newer.
- A working internet connection.
- Enough free space for both temporary files and the completed download.

## 2. First launch

The first startup may take several seconds while Y2Akio extracts and initializes its embedded Python, `yt-dlp`, and FFmpeg runtime. The splash screen stays visible during this preparation so the main screen is ready when it appears.

If Android asks for notification permission, allow it. Y2Akio uses notifications to show progress and completion for background downloads. The app can still open without this permission, but background status will be less visible.

## 3. Download a video or audio track

1. Copy a public media URL from a browser or another app.
2. Open Y2Akio.
3. Paste the URL into the link field.
4. Tap **Analyze**.
5. Wait for the title, thumbnail, and available formats to appear.
6. Choose **Video** or **Audio**.
7. Adjust the options you need.
8. Tap the download button.

The job appears in the active download queue. You can leave the app while it is running; the foreground service continues the work and reports progress through a notification.

### Video options

- **Quality:** choose the best available quality or set a maximum resolution.
- **Container:** MP4, MKV, or WebM when compatible with the selected streams.
- **Codec:** Automatic keeps the source codec when possible. H.264, HEVC, AV1, VP9, and MPEG-4 choices can force conversion.
- **Filename:** optionally prefix the title with the download date/time or channel name.

Keep **Codec** set to **Automatic** unless you need a specific playback format. Re-encoding can be much slower, increase battery use and device temperature, and reduce quality.

### Audio options

- Choose an audio output format such as MP3.
- Choose a bitrate when the format supports it.
- Use the filename prefix options if desired.

Converting audio does not improve the quality of the original stream. A higher output bitrate can create a larger file without adding detail.

### Subtitles, metadata, and thumbnails

- Enable subtitles and select manual tracks, automatically generated tracks, or both when available.
- Select a language or request all available languages.
- Choose a subtitle format, such as SRT.
- Embed subtitles in a compatible media container or save them as separate files.
- Embed metadata in the output when supported.
- Embed the thumbnail, save it as a separate image, or disable thumbnail output.

Not every website or format supplies all of these options.

## 4. Download a playlist

1. Paste a playlist URL and tap **Analyze**.
2. Select or clear individual entries in the playlist list.
3. Set the shared download options for the selected entries.
4. Start the batch download.

Playlist analysis is limited to the first 500 entries. Each selected item is queued as an individual download, so large playlists can take a long time and use significant storage.

## 5. Save a photo or gallery post

Y2Akio has dedicated gallery handling for:

- TikTok photo posts.
- X photo posts, including mixed photo/video posts.
- Facebook photo posts and stories.
- Instagram posts, carousels, and stories.

To save a gallery:

1. Paste the post URL and tap **Analyze**.
2. Tap items to select or clear them.
3. Long-press an item when you want to preview it.
4. Choose whether to save the result as a folder or a ZIP archive.
5. Start the download for all items or only the selected items.

Video-only social posts normally use the standard `yt-dlp` video flow instead of the gallery picker.

## 6. Download from the Android share sheet

When another app shares a text link, Android can show two Y2Akio targets:

- **Y2Akio · Open app:** opens Y2Akio, fills the shared URL, and starts analysis so you can review the options.
- **Y2Akio · Background download:** immediately queues the link using the defaults saved in **Settings > Background download defaults**, then returns you to the source app.

Configure the background mode before relying on it. Available defaults include video/audio mode, maximum video quality, container, audio format, thumbnail behavior, and gallery folder/ZIP output.

The background target extracts the first `http://` or `https://` link from the shared text.

## 7. Choose where files are saved

By default, completed files are saved under:

```text
Download/Y2Akio
```

To use another location:

1. Open **Settings**.
2. Select the video or audio save-folder setting.
3. Choose a folder in Android's system folder picker.
4. Confirm access when prompted.

Video/gallery and audio destinations can be configured separately. Folder access is managed by Android's Storage Access Framework, so Y2Akio does not need unrestricted access to device storage.

## 8. Use cookies for login-only content

Some private, age-restricted, followed-account, or story content is visible only to a logged-in account. Y2Akio can import a Netscape-format `cookies.txt` file exported from your own browser session.

### Import cookies

1. Export a Netscape-format `cookies.txt` from a browser where you are already signed in to the required website.
2. Open **Settings > Cookies** in Y2Akio.
3. Select **Import cookies.txt**.
4. Choose the exported file.

Y2Akio validates the file structure and stores its contents encrypted with a key protected by Android Keystore. Temporary plaintext copies are placed only in private app storage while an operation needs them, then deleted.

### Cookie safety

- Treat `cookies.txt` like a password: anyone with it may be able to access your signed-in session.
- Never download cookie files from another person or untrusted website.
- Do not send the file in messages, attach it to bug reports, or commit it to source control.
- Remove imported cookies from Y2Akio when you no longer need them.
- Re-export and import the file if the website logs you out or the session expires.

Imported browser cookies are not passed to YouTube in the current flow because a whole-browser cookie jar can trigger its bot protection.

## 9. Manage active downloads and history

The home screen shows active and recent jobs. Depending on the job state, you can:

- View progress and status messages.
- Cancel a running download.
- Dismiss a finished or failed queue item.
- Inspect the job log for diagnostic details.

The **Download History** section records completed downloads. Its actions can open the file, share it, copy its path, or remove the history entry. Clearing an entry from history does not necessarily delete the downloaded file; use the file action offered by the app or Android's file manager when you intend to remove the media itself.

## 10. Settings reference

- **Language:** English or Vietnamese.
- **Theme:** system, light, or dark.
- **Video folder:** destination for video and gallery output.
- **Audio folder:** destination for extracted audio.
- **Background download defaults:** options used by the no-UI share target.
- **Cookies:** import or remove the encrypted browser cookie jar.
- **yt-dlp:** view the installed version and check for an update.
- **Notifications:** open Android's notification settings for Y2Akio.
- **About and legal:** app information and responsible-use notice.

## 11. Troubleshooting

### The splash screen stays visible for a long time

The first launch extracts roughly 30 MB of runtime files and is normally slower than later launches. Keep the app open, confirm that the device has free storage, and try launching it again if initialization was interrupted. The splash has a timeout fallback, so it should not remain indefinitely.

### Analysis fails or says the URL is unsupported

- Confirm that the complete URL was pasted and opens in a browser.
- Check the network connection and try again.
- Open **Settings** and update `yt-dlp`.
- Import fresh cookies if the content requires a logged-in account.
- The post may be deleted, expired, private, region-restricted, or unavailable to your account.
- The website may have changed its page or API. In that case, the app or `yt-dlp` extractor may need an update.

### A story or private post cannot be found

Facebook and Instagram stories require valid cookies from a browser session that can view the story. Stories may also expire after 24 hours. Protected X content requires cookies from an account that is allowed to view the post.

### The download is slow or the phone becomes warm

- Use **Automatic** codec to avoid video re-encoding.
- Choose a lower maximum resolution.
- Avoid running several high-resolution conversions at once.
- Keep enough free space for intermediate audio/video streams before they are merged.

### No progress notification appears

Open Android **Settings > Apps > Y2Akio > Notifications** and enable notifications. Battery restrictions or manufacturer-specific background limits can also hide or stop long-running work.

### The download completed but the file is hard to find

Check `Download/Y2Akio`, the custom folder selected in Settings, the completed notification, or Download History. Gallery folder output may create a per-post subfolder; ZIP mode creates one archive instead.

### A downloaded file does not play

The selected container or codec may not be supported by the player. Try MP4 with H.264 for broad compatibility, understanding that forcing H.264 may require a slower re-encode. Alternatively, use **Automatic** codec and play the result with a player that supports the source codec.

### Imported cookies stopped working

Browser sessions expire or are invalidated by websites. Export a new Netscape `cookies.txt` while logged in, remove the old file from Y2Akio, and import the new one.

## 12. Responsible use and privacy

Y2Akio sends requests to the media website and to resources required by that website's extraction flow. It stores settings, download history, and encrypted cookies locally. Downloaded files remain on the device or in the folder you selected.

Do not use Y2Akio to bypass access controls, copy media without authorization, or violate a platform's terms. If you are unsure whether you may download something, obtain permission from the rights holder first.

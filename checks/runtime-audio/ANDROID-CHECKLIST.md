# Android verification still required

Local JVM/API compilation is not a substitute for these device tests.

1. In a running creator project, PICK -> select real MP3 -> IMPORT -> save ID with level -> PLAY.
2. Repeat for OGG Vorbis and, if supported by that phone, OGG Opus. Import a renamed non-audio file and truncated files; check error result and no published ID.
3. Cancel the system picker with Back, including a null Intent. Confirm following blocks continue and no audio ID is saved.
4. Press Add repeatedly while picker/import/preparation is outstanding: BUSY, one picker, no overwritten token. Cancel during scene transition; late Activity result must be ignored.
5. PAUSE/RESUME; SEEK 1.25 seconds (and beyond end); VOLUME 28/0/100; negative, NaN and >100 errors. Watch AUDIO_ERROR for asynchronous playback errors.
6. Background while picking, copying, preparing and playing. Explicit pause must survive background/foreground. Do not automatically reopen a picker after process death.
7. Delete original source; finish/restart project and restart app; saved ID must still resolve and play. Share same ID between two levels.
8. Finish project and change scenes while playing/importing/seeking. Verify cancelled jobs and released player, no unexpected resumed sound.
9. Full project copy/export/import onto another device: same ID, title and duration, playable bytes. Share only level JSON: AUDIO_EXISTS=0 and PLAY=error:MISSING_AUDIO; UI must say missing music.
10. Export state via Assistant: four-file/partial flow still works, runtime catalogue matches installed version; no audio payload or phone URI reaches bridge. Old host must not advertise these blocks.

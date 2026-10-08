# Run from the project root; ffmpeg must be installed.
from pathlib import Path
import subprocess
root = Path("app/src/androidTest/assets/player-fixtures")
root.mkdir(parents=True, exist_ok=True)
commands = [['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i', 'testsrc2=size=320x180:rate=12', '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=44100', '-t', '12', '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '32', '-g', '24', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '32k', '-movflags', '+faststart', 'sample.mp4'], ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', 'sample.mp4', '-c', 'copy', '-hls_time', '2', '-hls_playlist_type', 'vod', 'stream.m3u8'], ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', 'sample.mp4', '-c', 'copy', '-seg_duration', '2', '-f', 'dash', 'stream.mpd']]
for command in commands:
    subprocess.run(command, cwd=root, check=True)

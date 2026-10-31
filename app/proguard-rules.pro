# Правила R8 для release-сборки.
# Compose, AndroidX и Media3 поставляют собственные consumer-правила, поэтому здесь дополнительных не требуется.
# Если после включения R8 что-то перестанет работать, добавляйте нужные -keep правила сюда.

# FFmpeg-декодер Media3 (ALAC): рендерер создаётся через рефлексию, а нативный код вызывает методы по имени.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**

# OpenCV uses JNI symbols derived from Java class and native method names.
# The optimized Android default rules keep native member names and their classes.
# No blanket keep rule for application or CameraX code: retain R8 optimization.

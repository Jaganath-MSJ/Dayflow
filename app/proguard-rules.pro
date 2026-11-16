# App-specific R8 keep rules for release builds.
# Android components (activities, receivers, workers) are kept via the manifest, and Room, WorkManager,
# Glance and DataStore ship their own consumer rules.

# Glance finds a widget's instances by its GlanceAppWidget class. R8's class merging otherwise folds the
# four Dayflow widgets into ONE class, so updating one widget type redraws every widget with that content
# (seen in 1.0.1 testing: Today and Habits widgets showed the Water widget). Keep each one distinct.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget

# Glance finds placed widgets by their GlanceAppWidget class; merged classes render the wrong layout.
-keep class org.pilou.betterbose.widget.** extends androidx.glance.appwidget.GlanceAppWidget { *; }

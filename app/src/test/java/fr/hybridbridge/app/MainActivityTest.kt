package fr.hybridbridge.app

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 35])
class MainActivityTest {
    @Test
    fun startsWithoutBluetoothConnection() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        try {
            val activity = controller.setup().get()
            assertNotNull(activity.findViewById<android.view.View>(android.R.id.content))
            val root = activity.window.decorView
            root.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, 1080, 1920)
            val bitmap = android.graphics.Bitmap.createBitmap(1080, 1920, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                root.draw(android.graphics.Canvas(bitmap))
            } finally {
                bitmap.recycle()
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

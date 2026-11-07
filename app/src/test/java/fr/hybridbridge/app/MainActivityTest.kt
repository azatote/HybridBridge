package fr.hybridbridge.app

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
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
            val appRoot = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                .getChildAt(0) as android.widget.LinearLayout
            val navigation = appRoot.getChildAt(1) as android.widget.RadioGroup
            val content = appRoot.getChildAt(2) as android.widget.FrameLayout
            assertEquals(3, navigation.childCount)
            val bitmap = android.graphics.Bitmap.createBitmap(1080, 1920, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                for (index in 0 until 3) {
                    navigation.check(navigation.getChildAt(index).id)
                    root.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
                    )
                    root.layout(0, 0, 1080, 1920)
                    root.draw(android.graphics.Canvas(bitmap))
                    assertEquals(android.view.View.VISIBLE, content.getChildAt(index).visibility)
                    for (otherIndex in 0 until 3) {
                        if (otherIndex != index) assertEquals(android.view.View.GONE, content.getChildAt(otherIndex).visibility)
                    }
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

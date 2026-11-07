package fr.hybridbridge.app

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    @Test
    fun startsWithoutBluetoothConnection() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        try {
            val activity = controller.setup().get()
            assertNotNull(activity.findViewById<android.view.View>(android.R.id.content))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

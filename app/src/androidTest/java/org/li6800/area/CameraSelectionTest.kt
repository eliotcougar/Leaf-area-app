package org.li6800.area

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class CameraSelectionTest {
    @Test fun selectedCameraSurvivesFailureRestartAndStaleCallbacks() {
        val test = InstrumentationRegistry.getInstrumentation()
        val app = test.targetContext.applicationContext as Application
        val store = ViewModelStore()
        test.runOnMainSync {
            val vm = AreaViewModel(app)
            store.put("first", vm)
            try {
                vm.selectCamera("rear/physical")
                val oldSession = vm.cameraSessionId
                vm.selectCamera("front")
                vm.cameraError(oldSession)
                assertEquals(InputMode.CAMERA, vm.state.mode)
                assertEquals("front", vm.state.cameraKey)
                vm.cameraError(vm.cameraSessionId)
                assertEquals(InputMode.FROZEN, vm.state.mode)
                assertEquals("front", vm.state.cameraKey)
                val restored = AreaViewModel(app)
                store.put("restored", restored)
                assertEquals("front", restored.state.cameraKey)
                restored.startCamera()
                assertEquals("front", restored.state.cameraKey)
            } finally {
                store.clear()
                app.getSharedPreferences("measurement-settings", 0).edit().remove("camera-key").commit()
            }
        }
    }
}

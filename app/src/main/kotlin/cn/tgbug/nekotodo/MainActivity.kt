package cn.tgbug.nekotodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.tgbug.nekotodo.domain.Appearance
import cn.tgbug.nekotodo.ui.NekoTodoRoot
import cn.tgbug.nekotodo.ui.theme.NekoBackground
import cn.tgbug.nekotodo.ui.theme.NekoTodoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as NekoTodoApp).container

        setContent {
            val appearance by container.settings.appearance
                .collectAsStateWithLifecycle(initialValue = Appearance())

            NekoTodoTheme(appearance = appearance) {
                NekoBackground(appearance = appearance) {
                    // 半透明底色:它和下面的磨砂背景图一起构成"玻璃"的第一层,
                    // 卡片与顶栏再用带透明度的表面色叠上去。
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        NekoTodoRoot(container = container)
                    }
                }
            }
        }
    }
}

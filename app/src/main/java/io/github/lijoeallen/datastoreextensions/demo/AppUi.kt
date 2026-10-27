package io.github.lijoeallen.datastoreextensions.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lijoeallen.datastoreextensions.PersistedStore

private val ScreenPadding = 16.dp
private val ItemSpacing = 8.dp

@Composable
fun AppUi() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            // 委托语法 + 自定义后缀（key = demo-xyz1）
            Button(onClick = { DemoState.xyz++ }) {
                Text("xyz: ${DemoState.xyz}")
            }
            // 纯注解语法，未标注自动纳入（key = demo-abc，属性名作末段）
            Button(onClick = { DemoState.abc++ }) {
                Text("abc: ${DemoState.abc}")
            }
            // 嵌套作用域：段逐层追加（key = demo-Audio-volume）
            Button(onClick = { DemoState.Audio.volume += 2 }) {
                Text("volume: ${DemoState.Audio.volume}")
            }
            // prefix 缺省的对象：前缀回退对象名（key = Counter-count）
            Button(onClick = { Counter.count++ }) {
                Text("count: ${Counter.count}")
            }
            // 恢复出厂：全部属性内存即时回默认值，磁盘异步删除
            Button(onClick = { PersistedStore.resetAll() }) {
                Text("reset all")
            }
        }
    }
}

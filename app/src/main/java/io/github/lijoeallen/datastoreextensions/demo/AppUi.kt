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

@Composable
fun AppUi() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 方式一：委托 + 自定义后缀（key = demo-xyz1）
            Button(onClick = { DemoState.xyz++ }) {
                Text("xyz: ${DemoState.xyz}")
            }
            // 方式二：纯注解，未标注自动纳入（key = demo-abc，属性名作后缀）
            Button(onClick = { DemoState.abc++ }) {
                Text("abc: ${DemoState.abc}")
            }
            // k 为空的对象：前缀回退对象名（key = Counter-count）
            Button(onClick = { Counter.count++ }) {
                Text("count: ${Counter.count}")
            }
        }
    }
}

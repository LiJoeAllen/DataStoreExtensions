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
            // 方式一：委托（key = demo-xyz1，@K(k=) 覆盖后缀）
            Button(onClick = { DemoState.xyz++ }) {
                Text("xyz: ${DemoState.xyz}")
            }
            // 方式二：纯注解（key = demo-abc，对象作用域自动纳入）
            Button(onClick = { DemoState.abc++ }) {
                Text("abc: ${DemoState.abc}")
            }
        }
    }
}

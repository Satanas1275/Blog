package studio.rocknite.blog.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import studio.rocknite.blog.network.AnalyticsSummary

@Composable
fun AnalyticsScreen(summary: AnalyticsSummary?) {
    Scaffold(topBar = { TopAppBar(title = { Text("Analytique") }) }) { padding ->
        if (summary == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Posts", summary.totalPosts.toString(), Modifier.weight(1f))
                StatCard("Vues (${summary.days}j)", summary.totalViews.toString(), Modifier.weight(1f))
            }

            Text("Vues par jour", style = MaterialTheme.typography.titleMedium)
            val entries = summary.viewsPerDay.mapIndexed { i, d -> i.toFloat() to d.count.toFloat() }
            if (entries.isNotEmpty()) {
                val model = entryModelOf(*entries.toTypedArray())
                Chart(
                    chart = lineChart(),
                    model = model,
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            } else {
                Text("Pas encore de données.", style = MaterialTheme.typography.bodyMedium)
            }

            Text("Pages les plus vues", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(summary.topPaths) { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(p.path)
                        Text(p.count.toString(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

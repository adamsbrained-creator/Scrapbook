package app.scrapbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.scrapbook.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ArchiveViewModel = viewModel()
            val theme by vm.theme.collectAsState()
            ScrapbookTheme(theme) {
                val nav = rememberNavController()
                NavHost(nav, "home") {
                    composable("home") { HomeScreen(vm, nav) }
                    composable("item/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        ItemScreen(vm, nav, it.arguments!!.getLong("id"))
                    }
                    composable("shelf/{name}", listOf(navArgument("name") { type = NavType.StringType })) {
                        ShelfScreen(vm, nav, it.arguments!!.getString("name")!!)
                    }
                    composable("search") { SearchScreen(vm, nav) }
                    composable("rediscover") { RediscoverScreen(vm, nav) }
                    composable("edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        EditorScreen(vm, nav, it.arguments!!.getLong("id"))
                    }
                    composable("collections") { CollectionsScreen(vm, nav) }
                    composable("layouts/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        LayoutScreen(vm, nav, it.arguments!!.getLong("id"))
                    }
                }
            }
        }
    }
}

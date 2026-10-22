package br.com.koalasale

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class LocalImportTest {
    @Test fun selectedLocalCatalogRefreshesVisibleDataAndInvalidImportPreservesIt() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.deleteDatabase("koalasale.db")
        val store = ViewModelStore()
        try {
            val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[SaleViewModel::class.java]
            withTimeout(15000) { vm.data.first { it.loaded } }
            val file = File(app.cacheDir, "catalog-fixture.json")
            file.writeText("""{"catalogId":"fixture","products":[{"sourceId":"fixture-1","name":"Produto fictício","category":"Teste","priceCents":100}]}""")
            vm.importCatalog(Uri.fromFile(file))
            val imported = withTimeout(15000) { vm.data.first { it.products.isNotEmpty() } }
            assertEquals("Produto fictício", imported.products.single().name)
            assertEquals(100L, imported.products.single().priceCents)
            withTimeout(15000) { vm.busy.first { !it } }
            file.writeText("{}")
            vm.importCatalog(Uri.fromFile(file))
            withTimeout(15000) { vm.busy.first { !it } }
            assertNotNull(vm.error.value)
            assertEquals(imported.products, vm.data.value.products)
        } finally { store.clear(); app.deleteDatabase("koalasale.db"); Dispatchers.resetMain() }
    }
}

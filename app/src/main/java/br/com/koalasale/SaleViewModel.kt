package br.com.koalasale

import android.app.Application
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

data class AppData(val customers: List<Customer> = emptyList(), val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(), val quotes: List<QuoteWithItems> = emptyList(),
    val purchases: List<PurchaseWithItems> = emptyList(), val loaded: Boolean = false)

class SaleViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SaleDatabase.create(application)
    val dao = database.dao()
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    private val notices = Channel<String>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    val data = combine(dao.customers(), dao.categories(), dao.products(), dao.quotes(), dao.purchases()) { customers, categories, products, quotes, purchases ->
        AppData(customers, categories, products, quotes, purchases, true)
    }.catch { error.value = "Não foi possível abrir os dados locais. Feche e abra o aplicativo novamente." }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    fun save(onSuccess: () -> Unit, message: String = "Cadastro salvo.", operation: suspend SaleDao.() -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { dao.operation(); onSuccess(); notices.send(message) }
            catch (e: CancellationException) { throw e }
            catch (e: SQLiteConstraintException) {
                error.value = if (e.message?.contains("UNIQUE", true) == true) "Já existe uma categoria com esse nome."
                    else "Este cadastro está em uso. Remova as referências antes de excluí-lo."
            }
            catch (e: IllegalArgumentException) { error.value = e.message ?: "Confira os dados informados." }
            catch (_: Exception) { error.value = "Não foi possível salvar. Seus registros anteriores foram preservados." }
            finally { busy.value = false }
        }
    }
    fun importCatalog(uri: Uri) {
        save({}, "Catálogo local importado. Cadastros existentes preservados.") {
            withContext(Dispatchers.IO) {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (out.size() <= 2_000_000) { val size = input.read(buffer); if (size < 0) break; out.write(buffer, 0, size) }; out.toByteArray() }
                require(bytes != null && bytes.size <= 2_000_000) { "Selecione um catálogo JSON de até 2 MB." }
                try { database.withTransaction { CatalogImporter.importOnce(database.openHelper.writableDatabase, bytes.toString(Charsets.UTF_8)) } }
                catch (e: org.json.JSONException) { throw IllegalArgumentException("Catálogo inválido. Confira o formato JSON.", e) }
            }
        }
    }
    override fun onCleared() { database.close() }
}

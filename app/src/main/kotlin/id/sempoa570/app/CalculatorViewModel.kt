package id.sempoa570.app

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.sempoa570.engine.CalculatorSnapshot
import id.sempoa570.ui.CalculatorController
import id.sempoa570.ui.SnapshotStore
import kotlinx.coroutines.flow.first

/**
 * Pemegang [CalculatorController] yang selamat dari rotasi dan perubahan konfigurasi. Logika
 * tombolnya ada di :ui supaya sama persis dengan versi desktop.
 */
class CalculatorViewModel(store: DataStore<CalculatorSnapshot>) : ViewModel() {
    val controller = CalculatorController(viewModelScope, DataStoreSnapshotStore(store))
}

/** Status dipulihkan sekali saat aplikasi dibuka dan disimpan tiap perubahan (PRD §6.3). */
private class DataStoreSnapshotStore(private val store: DataStore<CalculatorSnapshot>) : SnapshotStore {
    override suspend fun load(): CalculatorSnapshot = store.data.first()
    override suspend fun save(snapshot: CalculatorSnapshot) {
        store.updateData { snapshot }
    }
}

package com.virtualsoft.firebase.services.firestore.database

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.*
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.virtualsoft.core.designpatterns.builder.IBuilder
import com.virtualsoft.core.utils.DateUtils.currentDate
import com.virtualsoft.core.utils.DateUtils.isBeforeDateTime
import com.virtualsoft.firebase.data.database.Metadata
import com.virtualsoft.firebase.data.database.IMetadata
import com.virtualsoft.firebase.services.firestore.FirestorePreferences
import com.virtualsoft.firebase.services.firestore.IFirestore
import com.virtualsoft.firebase.utils.LogUtils
import kotlinx.coroutines.tasks.await

class FirestoreDatabase(override var context: Context? = null) :
    IFirestore, IFirestoreDatabase {

    data class Properties(var dataTypeResolver: ((String) -> Class<out IDocument>)? = null)

    private var metadataSnapshotMap = hashMapOf<String, DocumentSnapshot>()
    private var firestoreDatabaseProperties: Properties? = null

    override val id: String
        get() = FirestoreDatabase::class.java.name

    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private enum class COLLECTIONS {
        metadata
    }

    class Builder(context: Context?) : IBuilder<IFirestore> {

        override val building =
            FirestoreDatabase(
                context
            )

        fun setFirestoreDatabaseProperties(firestoreDatabaseProperties: Properties?): Builder {
            building.firestoreDatabaseProperties = firestoreDatabaseProperties
            return this
        }
    }

    private fun addMetadataSnapshotListener(metadataId: String) {
        firestore.collection(COLLECTIONS.metadata.name).document(metadataId).addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
            when {
                firebaseFirestoreException != null -> {
                    LogUtils.logError("READ_METADATA", "snapshot fault - cannot read metadata from firestore: $metadataId", firebaseFirestoreException)
                }
                documentSnapshot?.exists() == true -> {
                    metadataSnapshotMap[metadataId] = documentSnapshot
                    FirestorePreferences.resetLastRead(metadataId, context)
                    LogUtils.logSuccess("READ_METADATA", "snapshot hit - read metadata from firestore success")
                }
                else -> {
                    LogUtils.logError("READ_METADATA", "snapshot fault - cannot read metadata from firestore: $metadataId")
                }
            }
        }
    }

    private suspend fun readMetadata(metadataId: String): IMetadata? {
        val metadataSnapshot = metadataSnapshotMap[metadataId]
        when {
            metadataSnapshot == null -> {
                addMetadataSnapshotListener(metadataId)
                return try {
                    val documentSnapshot = firestore.collection(COLLECTIONS.metadata.name).document(metadataId).get().await()
                    val metadata = documentSnapshot.toObject<Metadata>()
                    LogUtils.logSuccess("READ_METADATA", "read metadata from firestore success")
                    metadata
                }
                catch (e: Exception) {
                    LogUtils.logError("READ_METADATA", "snapshot fault - cannot read metadata from firestore: $metadataId", e)
                    null
                }
            }
            metadataSnapshot.exists() -> {
                val metadata = metadataSnapshot.toObject<Metadata>()
                LogUtils.logSuccess("READ_METADATA", "snapshot hit - read metadata from firestore success")
                return metadata
            }
            else -> {
                LogUtils.logError("READ_METADATA", "snapshot fault - cannot read metadata from firestore: $metadataId")
                return null
            }
        }
    }

    private suspend fun writeMetadata(metadataId: String, metadata: IMetadata): Boolean {
        return try {
            firestore.collection(COLLECTIONS.metadata.name).document(metadataId).set(metadata).await()
            LogUtils.logSuccess("WRITE_METADATA", "write metadata to firestore success")
            true
        }
        catch (e: Exception) {
            LogUtils.logError("WRITE_METADATA", "cannot write metadata to firestore: $metadataId", e)
            false
        }
    }

    private suspend fun updateMetadata(metadataId: String, field: String, value: Any): Boolean {
        return try {
            firestore.collection(COLLECTIONS.metadata.name).document(metadataId).update(field, value).await()
            LogUtils.logSuccess("UPDATE_METADATA", "update metadata to firestore success")
            true
        }
        catch (e: Exception) {
            LogUtils.logError("UPDATE_METADATA", "cannot update metadata to firestore: $metadataId", e)
            false
        }
    }

    private suspend fun deleteMetadata(metadataId: String): Boolean {
        return try {
            firestore.collection(COLLECTIONS.metadata.name).document(metadataId).delete().await()
            LogUtils.logSuccess("DELETE_METADATA", "delete metadata from firestore success")
            true
        }
        catch (e: Exception) {
            LogUtils.logError("DELETE_METADATA", "cannot delete metadata from firestore: $metadataId", e)
            false
        }
    }

    override suspend fun readDocument(documentReference: DocumentReference): IDocument? {
        //val metadataId = documentReference.path.replace("/", "")
        //val metadata = readMetadata(metadataId)
        var source = Source.DEFAULT
        //val lastRead = FirestorePreferences.getLastRead(metadataId, context)
        //val lastUpdate = metadata?.lastUpdate
        //if (lastRead != null && lastUpdate?.isBeforeDateTime(lastRead) == true) {
        //    Log.d("READ_DATA", "reading from cache")
        //    source = Source.CACHE
        //}
        //else
        //    Log.d("READ_DATA", "reading from server")
        return try {
            val document = documentReference.get(source).await()
            //FirestorePreferences.setLastRead(metadataId, currentDate(), context)
            val classType = firestoreDatabaseProperties?.dataTypeResolver?.invoke(document.get("type").toString())
            document.toObject(classType!!)
        }
        catch (e: Exception) {
            LogUtils.logError(
                "READ_DATA",
                "cannot read data at the specified path: ${documentReference.path}",
                e
            )
            null
        }
    }

    override suspend fun readCollection(collectionReference: CollectionReference): List<IDocument> {
        //val metadataId = collectionReference.path.replace("/", "")
        //val metadata = readMetadata(metadataId)
        var source = Source.DEFAULT
        //val lastRead = FirestorePreferences.getLastRead(metadataId, context)
        //val lastUpdate = metadata?.lastUpdate
        //if (lastRead != null && lastUpdate?.isBeforeDateTime(lastRead) == true) {
        //    Log.d("READ_DATA", "reading from cache")
        //    source = Source.CACHE
        //}
        //else
        //    Log.d("READ_DATA", "reading from server")
        return try {
            val collection = collectionReference.get(source).await()
            //FirestorePreferences.setLastRead(metadataId, currentDate(), context)
            val list = mutableListOf<IDocument>()
            for (document in collection) {
                val classType = firestoreDatabaseProperties?.dataTypeResolver?.invoke(document.get("type").toString())
                list.add(document.toObject(classType!!))
            }
            list
        }
        catch (e: Exception) {
            LogUtils.logError(
                "READ_DATA",
                "cannot read data at the specified path: ${collectionReference.path}",
                e
            )
            listOf()
        }
    }

    override suspend fun readCollection(collectionReference: CollectionReference, query: Query): List<IDocument> {
        //val metadataId = collectionReference.path.replace("/", "")
        //val metadata = readMetadata(metadataId)
        var source = Source.DEFAULT
        //val lastRead = FirestorePreferences.getLastRead(metadataId, context)
        //val lastUpdate = metadata?.lastUpdate
        //if (lastRead != null && lastUpdate?.isBeforeDateTime(lastRead) == true) {
        //    Log.d("READ_DATA", "reading from cache")
        //    source = Source.CACHE
        //}
        //else
        //    Log.d("READ_DATA", "reading from server")
        return try {
            val collection = query.get(source).await()
            //FirestorePreferences.setLastRead(metadataId, currentDate(), context)
            val list = mutableListOf<IDocument>()
            for (document in collection) {
                val classType = firestoreDatabaseProperties?.dataTypeResolver?.invoke(document.get("type").toString())
                list.add(document.toObject(classType!!))
            }
            list
        }
        catch (e: Exception) {
            LogUtils.logError(
                "READ_DATA",
                "cannot read data at the specified path: ${collectionReference.path}",
                e
            )
            listOf()
        }
    }

    override suspend fun writeDocument(documentReference: DocumentReference, data: IDocument): Boolean {
        return try {
            val currentDate = currentDate()
            data.lastUpdate = currentDate
            documentReference.set(data).await()
            //val documentMetadataId = documentReference.path.replace("/", "")
            //val collectionMetadataId = documentReference.parent.path.replace("/", "")
            //val documentMetadata = Metadata.buildMetadata(documentMetadataId, context).apply {
            //    lastUpdate = currentDate
            //}
            //val collectionMetadata = Metadata.buildMetadata(collectionMetadataId, context).apply {
            //    lastUpdate = currentDate
            //}
            //writeMetadata(documentMetadataId, documentMetadata)
            //writeMetadata(collectionMetadataId, collectionMetadata)
            true
        }
        catch (e: Exception) {
            LogUtils.logError(
                "WRITE_DATA",
                "cannot write data at the specified path: ${documentReference.path}",
                e
            )
            false
        }
    }

    override suspend fun updateDocument(documentReference: DocumentReference, field: String, value: Any): Boolean {
        return try {
            val currentDate = currentDate()
            documentReference.update(field, value).await()
            if (field != IDocument::lastUpdate.name)
                documentReference.update(IDocument::lastUpdate.name, currentDate).await()
            //val documentMetadataId = documentReference.path.replace("/", "")
            //val collectionMetadataId = documentReference.parent.path.replace("/", "")
            //updateMetadata(documentMetadataId, IMetadata::lastUpdate.name, currentDate)
            //updateMetadata(collectionMetadataId, IMetadata::lastUpdate.name, currentDate)
            true
        }
        catch (e: Exception) {
            LogUtils.logError(
                "UPDATE_DATA",
                "cannot update data at the specified path: ${documentReference.path}",
                e
            )
            false
        }
    }

    override suspend fun deleteDocument(documentReference: DocumentReference): Boolean {
        return try {
            documentReference.delete().await()
            //val documentMetadataId = documentReference.path.replace("/", "")
            //val collectionMetadataId = documentReference.parent.path.replace("/", "")
            //deleteMetadata(documentMetadataId)
            //updateMetadata(collectionMetadataId, IMetadata::lastUpdate.name, currentDate())
            true
        }
        catch (e: Exception) {
            LogUtils.logError(
                "DELETE_DATA",
                "cannot delete data at the specified path: ${documentReference.path}",
                e
            )
            false
        }
    }
}
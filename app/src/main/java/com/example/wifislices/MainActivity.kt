package com.example.wifislices

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.SettingsSlicesContract
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.slice.SliceManager
import androidx.slice.SliceMetadata
import androidx.slice.SliceProvider
import androidx.slice.SliceViewManager
import androidx.slice.compat.SliceProviderCompat
import androidx.slice.core.SliceHints
import androidx.slice.widget.SliceLiveData
import androidx.slice.widget.SliceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "WifiSlices"

class MainActivity : AppCompatActivity() {

    private lateinit var sliceView: SliceView
    private lateinit var textView: TextView

    private val sliceUri = SettingsSlicesContract.BASE_URI.buildUpon()
            .appendPath(SettingsSlicesContract.PATH_SETTING_ACTION)
            .appendPath(SettingsSlicesContract.KEY_WIFI)
            .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.textView)
        textView.text = sliceUri.toString()

        val sliceManager = SliceViewManager.getInstance(this)
        lifecycleScope.launch {
            lateinit var uris: Collection<Uri>
            withContext(Dispatchers.Default) {
                // This call is required to get the SettingsSliceProvider to whitelist this app.
                // SettingsSliceProvider#grantWhitelistedPackagePermissions is only called inside of
                // SettingsSliceProvider#onGetSliceDescendants.
                // The Settings app whitelisting this WifiSlices example app is the only way to
                // display the slice properly, because the SettingsSliceProvider just has a no-op
                // PendingIntent when slice permissions are requested.
                uris = sliceManager.getSliceDescendants(
                        SettingsSlicesContract.BASE_URI.buildUpon()
                                .appendPath(SettingsSlicesContract.PATH_SETTING_ACTION)
                                .build())
            }
            Toast.makeText(this@MainActivity, "$uris", Toast.LENGTH_SHORT).show()

            sliceView = findViewById(R.id.slice)
            sliceView.bind(this@MainActivity, this@MainActivity, sliceUri)
        }
    }
}

/**
 * Extension function from SliceViewerKotlin sample (user-interface-samples)
 */
fun SliceView.bind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        uri: Uri,
        onSliceActionListener: SliceView.OnSliceActionListener = SliceView.OnSliceActionListener { _, _ -> },
        onClickListener: View.OnClickListener = View.OnClickListener { },
        onLongClickListener: View.OnLongClickListener = View.OnLongClickListener { false },
        scrollable: Boolean = false
) {
    setOnSliceActionListener(onSliceActionListener)
    setOnClickListener(onClickListener)
    isScrollable = scrollable
    setOnLongClickListener(onLongClickListener)
    if (uri.scheme == null) {
        Log.w(TAG, "Scheme is null for URI $uri")
        return
    }
    // If someone accidentally prepends the "slice-" prefix to their scheme, let's remove it.
    val scheme =
            if (uri.scheme!!.startsWith("slice-")) {
                uri.scheme!!.replace("slice-", "")
            }
            else {
                uri.scheme
            }
    if (scheme == ContentResolver.SCHEME_CONTENT ||
            scheme.equals("https", true) ||
            scheme.equals("http", true)
    ) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val sliceLiveData = SliceLiveData.fromIntent(context, intent)
        sliceLiveData.removeObservers(lifecycleOwner)
        try {
            sliceLiveData.observe(lifecycleOwner, Observer { updatedSlice ->
                if (updatedSlice == null) return@Observer
                slice = updatedSlice
                val expiry = SliceMetadata.from(context, updatedSlice).expiry
                if (expiry != SliceHints.INFINITY) {
                    // Shows the updated text after the TTL expires.
                    postDelayed(
                            { slice = updatedSlice },
                            expiry - System.currentTimeMillis() + 15
                    )
                }
                Log.d(TAG, "Update Slice: $updatedSlice")
            })
        } catch (e: Exception) {
            Log.e(
                    TAG,
                    "Failed to find a valid ContentProvider for authority: $uri"
            )
        }
    } else {
        Log.w(TAG, "Invalid uri, skipping slice: $uri")
    }
}

package com.axalocklibs

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.axalocklibs.`interface`.IAPIAxaLockCallback
import com.axalocklibs.singletone.AxaSingleToneClass

class MainActivity : AppCompatActivity(), IAPIAxaLockCallback {

    private lateinit var tvConnect: TextView
    private lateinit var tvDisConnect: TextView
    private lateinit var tvLockStatus: TextView
    private lateinit var edtAssetID: EditText
    private lateinit var edtMacID: EditText
    private lateinit var prgBar: ProgressBar

    var erlSingleToneClass: AxaSingleToneClass? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvConnect = findViewById(R.id.tvConnect)
        tvDisConnect = findViewById(R.id.tvDisConnect)
        tvLockStatus = findViewById(R.id.tvLockStatus)
        edtAssetID = findViewById(R.id.edtAssetID)
        edtMacID = findViewById(R.id.edtMacID)
        prgBar = findViewById(R.id.prgBar)

        val animZoomOut =
            AnimationUtils.loadAnimation(
                this@MainActivity,
                R.anim.button_zoom_out_animation
            )

        tvConnect.setOnClickListener {

            if (edtAssetID.text.equals("")) {
                Toast.makeText(this@MainActivity, "Please Enter Asset ID", Toast.LENGTH_SHORT)
                    .show()
            } else if (edtMacID.text.equals("")) {
                Toast.makeText(this@MainActivity, "Please Enter MAC ID", Toast.LENGTH_SHORT).show()
            } else {
                tvLockStatus.setText("Clicked Connect.")
                tvConnect.startAnimation(animZoomOut)
                prgBar.visibility = View.VISIBLE
                erlSingleToneClass!!.onUpdateAxaEKey(
                    2, edtMacID.text.toString(),
                    edtAssetID.text.toString(), 0, 1 ,
                    "https://kolonishare.com/design/ws/v11/update_ekey",
                    "5.1.3" ,"Basic YWRtaW46MTIzNA=="
                )
            }
        }

        tvDisConnect.setOnClickListener {
            tvDisConnect.startAnimation(animZoomOut)
            erlSingleToneClass!!.onDisconnectAxa()
        }
    }

    override fun onStart() {
        super.onStart()
        erlSingleToneClass = AxaSingleToneClass()
        erlSingleToneClass!!.serviceInit(this@MainActivity, this@MainActivity)

    }

    override fun onPause() {
        super.onPause()
        erlSingleToneClass!!.removeBroadcast()
    }

    override fun onDestroy() {
        super.onDestroy()
        erlSingleToneClass!!.removeBroadcast()
    }

    override fun onAxaConnected(eventType: String, lockValue: String) {
        prgBar.visibility = View.GONE
        tvLockStatus.setText("Connected.")
        tvConnect.setText("Connected")
    }

    override fun onAxaDisconnected(eventType: String, lockValue: String) {
        prgBar.visibility = View.GONE
        tvLockStatus.setText("Disconnected.")
        tvConnect.setText("Connect")
    }

    override fun onAxaLockUnLockSuccessfully(eventType: String, lockValue: String) {
        prgBar.visibility = View.GONE
        tvLockStatus.setText("Lock/Unock Success.")
    }

    override fun onAxaDiscovered(eventType: String, lockValue: String) {
        tvLockStatus.setText("Discovered...")
    }

    override fun onAxaERLNotFound(eventType: String, lockValue: String) {
        prgBar.visibility = View.GONE
        tvLockStatus.setText("ERL Not found.")
        tvConnect.setText("Connect")
    }

    override fun onAxaStartEkeyUpdate(eventType: String, lockValue: String) {
        tvLockStatus.setText("Updating Ekey and EPass Key...")
    }

    override fun onAxaEkeyUpdatedSuccessfully(eventType: String, lockValue: String) {
        prgBar.visibility = View.GONE
        tvLockStatus.setText("Ekey and EPass Key Updated")
    }

    override fun onAxaConnecting(eventType: String, lockValue: String) {
        tvLockStatus.setText("Connecting...")
    }

    override fun onAxaStartLockUnlock(eventType: String, lockValue: String) {
        val handler = Handler()
        handler.postDelayed({
            prgBar.visibility = View.VISIBLE
            erlSingleToneClass!!.lockAndUnlockAxaLock()
        }, 1000)
    }
}
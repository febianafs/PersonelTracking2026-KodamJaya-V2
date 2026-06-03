package com.example.personeltracking2026.ui.bluetooth

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.personeltracking2026.R

/**
 * Adapter manual (bukan RecyclerView) karena list-nya dimasukkan ke
 * LinearLayout di dalam ScrollView — sehingga tidak ada nested scroll conflict.
 *
 * Gunakan [renderInto] untuk merender ulang seluruh list ke [container].
 */
class BluetoothDeviceAdapter(
    private val context: Context,
    private val onConnect: (BluetoothDeviceModel) -> Unit,
    private val onDisconnect: (BluetoothDeviceModel) -> Unit
) {

    fun renderInto(container: LinearLayout, devices: List<BluetoothDeviceModel>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)

        for (device in devices) {
            val itemView = inflater.inflate(R.layout.item_bluetooth_device, container, false)

            val tvName      = itemView.findViewById<TextView>(R.id.tvDeviceName)
            val tvMac       = itemView.findViewById<TextView>(R.id.tvDeviceMac)
            val tvRssi      = itemView.findViewById<TextView>(R.id.tvRssi)
            val btnConnect    = itemView.findViewById<TextView>(R.id.btnConnect)
            val btnConnecting = itemView.findViewById<TextView>(R.id.btnConnecting)
            val badgeConnected = itemView.findViewById<TextView>(R.id.badgeConnected)
            val btnDisconnect  = itemView.findViewById<TextView>(R.id.btnDisconnect)

            // ── Isi data ──────────────────────────────────────────────────
            tvName.text = device.name.ifBlank { "Unknown Device" }
            tvMac.text  = device.address
            tvRssi.text = "${device.rssi} dBm"

            // ── Highlight border jika connected ───────────────────────────
            val cardParent = itemView as androidx.cardview.widget.CardView
            if (device.state == DeviceState.CONNECTED) {
                cardParent.setCardBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(context, R.color.background2)
                )
                // Anda bisa set stroke via ShapeDrawable jika perlu border hijau
            }

            // ── State tombol ──────────────────────────────────────────────
            btnConnect.visibility    = View.GONE
            btnConnecting.visibility = View.GONE
            badgeConnected.visibility = View.GONE
            btnDisconnect.visibility  = View.GONE

            when (device.state) {
                DeviceState.IDLE -> {
                    btnConnect.visibility = View.VISIBLE
                    btnConnect.setOnClickListener { onConnect(device) }
                }
                DeviceState.CONNECTING -> {
                    btnConnecting.visibility = View.VISIBLE
                }
                DeviceState.CONNECTED -> {
                    badgeConnected.visibility = View.VISIBLE
                    btnDisconnect.visibility  = View.VISIBLE
                    btnDisconnect.setOnClickListener { onDisconnect(device) }
                }
            }

            container.addView(itemView)
        }
    }
}
package com.example.personeltracking2026kodamjayav2.ui.bluetooth

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.example.personeltracking2026kodamjayav2.R

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
            val cardParent = itemView as CardView

            val tvName = itemView.findViewById<TextView>(R.id.tvDeviceName)
            val tvMac = itemView.findViewById<TextView>(R.id.tvDeviceMac)
            val tvRssi = itemView.findViewById<TextView>(R.id.tvRssi)
            val btnConnect = itemView.findViewById<TextView>(R.id.btnConnect)
            val btnConnecting = itemView.findViewById<TextView>(R.id.btnConnecting)
            val btnDisconnect = itemView.findViewById<TextView>(R.id.btnDisconnect)

            tvName.text = device.name.ifBlank { "Unknown Device" }
            tvMac.text = device.address
            tvRssi.text = "${device.rssi} dBm"

            cardParent.setCardBackgroundColor(
                ContextCompat.getColor(context, R.color.background2)
            )
            cardParent.isClickable = false
            cardParent.isFocusable = false
            cardParent.isEnabled = true
            cardParent.setOnClickListener(null)

            btnConnect.visibility = View.GONE
            btnConnecting.visibility = View.GONE
            btnDisconnect.visibility = View.GONE

            when (device.state) {
                DeviceState.IDLE -> {
                    btnConnect.visibility = View.VISIBLE
                    val connectClick = View.OnClickListener {
                        btnConnect.isEnabled = false
                        cardParent.isEnabled = false
                        onConnect(device)
                    }
                    btnConnect.setOnClickListener(connectClick)
                    cardParent.isClickable = true
                    cardParent.isFocusable = true
                    cardParent.setOnClickListener(connectClick)
                }

                DeviceState.CONNECTING -> {
                    btnConnecting.visibility = View.VISIBLE
                    cardParent.isEnabled = false
                }

                DeviceState.CONNECTED -> {
                    btnDisconnect.visibility = View.VISIBLE
                    btnDisconnect.setOnClickListener {
                        btnDisconnect.isEnabled = false
                        onDisconnect(device)
                    }
                }
            }

            container.addView(itemView)
        }
    }
}

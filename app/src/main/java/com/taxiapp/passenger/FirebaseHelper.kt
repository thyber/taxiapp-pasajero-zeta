package com.taxiapp.passenger

import com.google.firebase.database.*
import org.json.JSONObject

class FirebaseHelper {
    
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val activeDriversRef: DatabaseReference = database.getReference("activeDrivers")
    private val activeRidesRef: DatabaseReference = database.getReference("activeRides")
    
    interface DriverLocationListener {
        fun onDriverLocationUpdate(driverId: String, lat: Double, lng: Double)
    }
    
    interface RideStatusListener {
        fun onRideStatusChanged(ride: JSONObject)
    }
    
    interface NewRideRequestListener {
        fun onNewRideRequest(ride: JSONObject)
    }
    
    fun updateDriverLocation(driverId: String, lat: Double, lng: Double) {
        val driverRef = activeDriversRef.child(driverId)
        driverRef.child("location").setValue(mapOf(
            "lat" to lat,
            "lng" to lng
        ))
    }
    
    fun goOnline(driverId: String, driverData: Map<String, Any?>) {
        val driverRef = activeDriversRef.child(driverId)
        driverRef.setValue(driverData)
    }
    
    fun goOffline(driverId: String) {
        activeDriversRef.child(driverId).removeValue()
    }
    
    fun requestRide(rideId: String, rideData: Map<String, Any?>, onComplete: (Boolean, String?) -> Unit) {
        val rideRef = activeRidesRef.child(rideId)
        rideRef.setValue(rideData)
            .addOnCompleteListener { task: com.google.android.gms.tasks.Task<Void> ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, task.exception?.message)
                }
            }
    }
    
    fun updateRideStatus(rideId: String, status: String, extraData: Map<String, Any?>? = null) {
        val rideRef = activeRidesRef.child(rideId)
        val updates = mutableMapOf<String, Any?>("status" to status)
        extraData?.let { updates.putAll(it) }
        rideRef.updateChildren(updates)
    }
    
    fun updatePassengerLocation(rideId: String, lat: Double, lng: Double) {
        val rideRef = activeRidesRef.child(rideId)
        rideRef.child("passengerLocation").setValue(mapOf(
            "lat" to lat,
            "lng" to lng,
            "timestamp" to ServerValue.TIMESTAMP
        ))
    }
    
    fun setPassengerSharingLocation(rideId: String, isSharing: Boolean) {
        val rideRef = activeRidesRef.child(rideId)
        rideRef.child("passengerSharingLocation").setValue(isSharing)
    }
    
    fun acceptRide(rideId: String, driverId: String, driverData: Map<String, Any?>) {
        val rideRef = activeRidesRef.child(rideId)
        rideRef.updateChildren(mapOf(
            "status" to "accepted",
            "driverId" to driverId,
            "driverData" to driverData,
            "acceptedAt" to ServerValue.TIMESTAMP
        ))
    }
    
    fun driverArrived(rideId: String) {
        val rideRef = activeRidesRef.child(rideId)
        rideRef.updateChildren(mapOf(
            "status" to "arrived",
            "arrivedAt" to ServerValue.TIMESTAMP
        ))
    }
    
    fun startRide(rideId: String) {
        val rideRef = activeRidesRef.child(rideId)
        rideRef.updateChildren(mapOf(
            "status" to "in_progress",
            "startedAt" to ServerValue.TIMESTAMP
        ))
    }
    
    fun completeRide(rideId: String, completedRideData: Map<String, Any?>) {
        val rideRef = activeRidesRef.child(rideId)
        rideRef.removeValue()
        
        val completedRidesRef = database.getReference("completedRides")
        completedRidesRef.push().setValue(completedRideData)
    }
    
    fun cancelRide(rideId: String) {
        val rideRef = activeRidesRef.child(rideId)
        rideRef.updateChildren(mapOf(
            "status" to "cancelled",
            "cancelledBy" to "passenger",
            "cancelledAt" to ServerValue.TIMESTAMP
        ))
    }
    
    fun cancelPendingRidesForPassenger(passengerId: String, onComplete: () -> Unit) {
        activeRidesRef.orderByChild("passengerId").equalTo(passengerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val status = child.child("status").getValue(String::class.java)
                        if (status == "pending") {
                            child.ref.removeValue()
                        }
                    }
                    onComplete()
                }
                
                override fun onCancelled(error: DatabaseError) {
                    onComplete()
                }
            })
    }
    
    fun passengerOnTheWay(rideId: String) {
        val rideRef = activeRidesRef.child(rideId)
        rideRef.updateChildren(mapOf(
            "passengerStatus" to "on_the_way",
            "passengerOnTheWayAt" to ServerValue.TIMESTAMP
        ))
    }
    
    fun getNearbyDrivers(vehicleType: String, callback: (List<Map<String, Any>>) -> Unit) {
        activeDriversRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val drivers = mutableListOf<Map<String, Any>>()
                for (driverSnapshot in snapshot.children) {
                    val driverData = driverSnapshot.value as? Map<String, Any> ?: continue
                    val status = driverData["status"] as? String ?: continue
                    val driverVehicleType = driverData["vehicleType"] as? String ?: continue
                    
                    if (status == "available") {
                        val matches = when (vehicleType) {
                            "auto" -> driverVehicleType == "Auto" || driverVehicleType == "Vagoneta"
                            "vagoneta" -> driverVehicleType == "Vagoneta"
                            "moto" -> driverVehicleType == "Moto"
                            else -> true
                        }
                        
                        if (matches) {
                            drivers.add(driverData.toMutableMap().apply { put("id", driverSnapshot.key ?: "") })
                        }
                    }
                }
                callback(drivers)
            }
            
            override fun onCancelled(error: DatabaseError) {
                callback(emptyList())
            }
        })
    }
    
    fun rateDriver(rideId: String, rating: Int, comment: String) {
        val completedRidesRef = database.getReference("completedRides")
        completedRidesRef.orderByChild("id").equalTo(rideId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        child.ref.updateChildren(mapOf(
                            "driverRating" to rating,
                            "driverComment" to comment
                        ))
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {}
            })
    }
    
    fun listenToDriverLocation(driverId: String, listener: DriverLocationListener) {
        activeDriversRef.child(driverId).child("location")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lat = snapshot.child("lat").getValue(Double::class.java) ?: return
                    val lng = snapshot.child("lng").getValue(Double::class.java) ?: return
                    listener.onDriverLocationUpdate(driverId, lat, lng)
                }
                
                override fun onCancelled(error: DatabaseError) {}
            })
    }
    
    fun listenToRideStatus(rideId: String, listener: RideStatusListener) {
        activeRidesRef.child(rideId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rideMap = snapshot.value as? Map<String, Any> ?: return
                val rideJson = JSONObject(rideMap)
                listener.onRideStatusChanged(rideJson)
            }
            
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    
    fun listenToNewRideRequests(listener: NewRideRequestListener) {
        activeRidesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val rideMap = snapshot.value as? Map<String, Any> ?: return
                val rideJson = JSONObject(rideMap)
                if (rideJson.optString("status") == "pending") {
                    listener.onNewRideRequest(rideJson)
                }
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }
    
    fun getSystemSettings(callback: (Map<String, Any>?) -> Unit) {
        database.getReference("systemSettings").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callback(snapshot.value as? Map<String, Any>)
            }
            
            override fun onCancelled(error: DatabaseError) {
                callback(null)
            }
        })
    }
    
    fun getAppVersion(callback: (Map<String, Any>?) -> Unit) {
        database.getReference("appVersions").child("passenger").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callback(snapshot.value as? Map<String, Any>)
            }
            
            override fun onCancelled(error: DatabaseError) {
                callback(null)
            }
        })
    }
}

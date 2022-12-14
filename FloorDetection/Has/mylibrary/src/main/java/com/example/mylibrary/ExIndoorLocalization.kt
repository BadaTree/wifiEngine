package com.example.mylibrary

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.util.Log
import android.widget.Toast
import com.example.heropdr.HeroPDR
import com.example.heropdr.MovingAverage
import com.example.heropdr.PDR
import com.example.mylibrary.filters.ParticleFilter
import com.example.mylibrary.floor.AppforContext
import com.example.mylibrary.floor.ElvPressure
import com.example.mylibrary.floor.MovingFloor
import com.example.mylibrary.floor.SuminAverage
import com.example.mylibrary.instant.InstantLocalization
import com.example.mylibrary.maps.BuilInfo
import com.example.mylibrary.maps.BuildingInfo
import com.example.mylibrary.maps.MagneticFieldMap
import com.example.mylibrary.maps.ResourceDataManager
import com.example.mylibrary.sensors.GetGyroscope
import com.example.mylibrary.sensors.VectorCalibration
import com.example.mylibrary.wifiengine.WifiMap_RSSI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.InputStream
import kotlin.math.round

class ExIndoorLocalization {

    constructor(magneticStream : InputStream?, magneticStreamForInstant : InputStream?, wifiStreamTotal : InputStream?, wifiStreamRSSI : InputStream?, wifiStreamUnq : InputStream?, inputStreamOfBuilding : InputStream , floorChange : Boolean) {
        resourceDataManager = ResourceDataManager(magneticStream, magneticStreamForInstant, wifiStreamTotal, wifiStreamRSSI, wifiStreamUnq)
        instantLocalization = InstantLocalization(resourceDataManager.magneticFieldMap, resourceDataManager.instantMap)
        wifiengine = WifiMap_RSSI(resourceDataManager.wifiDataMap)
        isFloorChange = floorChange
        movingFloor = MovingFloor()
        buildingInfo = BuildingInfo(inputStreamOfBuilding)
    }

    private val isFloorChange : Boolean
    private lateinit var mapAfterFloorChange : MagneticFieldMap
    private lateinit var mapInstantAfterFloorChange : MagneticFieldMap
    private var isSensorStabled : Boolean = false
    private var magStableCount : Int = 100
    private var accStableCount : Int = 50
    private var returnGyro : String = "0.0"
    private var returnState : String = "not support"
    private var returnIns : String = "not support"
    private var returnX : String = "unknown"
    private var returnY : String = "unknown"
    private var resourceDataManager : ResourceDataManager

    private var magMatrix = FloatArray(3)
    private var accMatrix = FloatArray(3)

    private val heroPDR : HeroPDR = HeroPDR()
    private lateinit var pdrResult : PDR
    private val accXMovingAverage : MovingAverage = MovingAverage(10)
    private val accYMovingAverage : MovingAverage = MovingAverage(10)
    private val accZMovingAverage : MovingAverage = MovingAverage(10)
    private var userDir : Double = 0.0
    private var stepCount : Int = 0
    private var devicePosture : Int = 0
    private var stepType : Int = 0
    private val poseTypes = arrayOf("On Hand", "In Pocket", "Hand Swing")
    private val stepTypes = arrayOf("normal", "fast", "slow", "prowl", "non")
    internal var particleOn : Boolean = false
    ///////////////pdrResult??? ?????? ??????////////////////////////
//                    data class PDR(
//                      val devicePosture : Int,
//                      val stepType : Int,
//                      val stepLength : Double,
//                      val direction : Double,
//                      val totalStepCount : Int
//                    )
    /////////////////////////////////////////////////////////

    // ?????????????????? ?????? // (??????)
    private val getGyroscope by lazy {
        GetGyroscope()
    }
    private var gyro_constant_from_app_start_to_map_collection : Float = 0f
    private var gyro_value_map_collection : Float = 0f
    private var gyro_value_reset_direction : Float = 0f
    /*
        ????????? ??? ??????
        1. gyro_constant_from_app_start_to_map_collection :
                    ?????? : degree
                    ?????? : -
                    ?????? : ??? ?????? ??????, [??? ?????? ??????]??? [??? ???????????? ??????]??? ????????? ?????? ??????.
                           ??? ?????? ????????? ?????? ????????????, ???????????? ???????????? ????????? ?????? ?????? ?????? ????????? ?????????.
                           ??????????????? ???????????? ?????? ???.

        2. gyro_value_map_collection :
                    ?????? : degree
                    ?????? : ????????? - ??? ???????????? ?????? / ????????? - ??? ?????? ??????
                    ?????? : gyro_value_reset_direction + gyroCalivalue ??? ????????????.
                           ??? ?????? ????????? ???????????? ????????? ???????????????, ?????? ?????? vector calibration??? ?????? ????????? ?????? ????????? ??????????????? ?????? ????????? ?????????.
                           Always On Instant ??? ?????? ????????? ?????????.
                           Particle Filter?????? ??????.

        3. gyro_value_reset_direction :
                    ?????? : degree
                    ?????? : gyro reset??? ?????? ?????? ??????
                    ?????? : gyro reset??? ??? ????????? ?????? ????????? ????????? ????????? ?????? ?????? ????????? ??????.
                           Always On Instant??? ?????? ?????????. ????????? ???????????? ?????? ???.
    */



    // Vector Calibration ?????? // (??????)
    private val vectorCalibration = VectorCalibration()
    private var caliVector: Array<Double> = arrayOf()
    private var caliVector_AON: Array<Double> = arrayOf()
    /*
        ?????????????????? ?????? ??? ??????
        1. caliVector
                    ?????? ?????? : ????????? ????????? '??? ?????? ??????'?????? ??????
                    ?????? : ???????????? ????????? ????????? ?????? '??? ?????? ??????' ?????? ??????.
                           ??????????????? ?????? ?????? ??? ????????? ?????????. ?????? ???????????? ??? ????????? ???????????? ???.

        2. caliVector_AON
                    ?????? ?????? : ????????? ????????? 'Always On??? ?????? ????????? ??? ????????? ??????'?????? ??????
                    ?????? : ???????????? ????????? ????????? 'Always On??? ?????? ????????? ??? ????????? ??????'?????? ??????.
                           ???????????? ?????? ????????? ??????????????? ??????, ??? ????????? ?????????. ???????????? ???????????? ???.
    */


    // ????????? ?????? ?????? // (??????)
    private var PFResult : Array<Double> = arrayOf(-1.0, -1.0)
    private lateinit var particleFilter: ParticleFilter

    // ???????????? ?????? // (??????)
    private var first_sampling : Boolean = true
    private var instantLocalization : InstantLocalization

    var wifi_range = arrayListOf(-100, -100, -100, -100)  // (??????) ??????????????? ??????????????? wifi_range. ??? ????????? ???????????? ????????? getLocation ???????????? ????????? ???????????? ???.
    private var ILResult : MutableMap<String, Float> = mutableMapOf("status_code" to -1.0f, "gyro_from_map" to -1.0f, "pos_x" to -1.0f, "pos_y" to -1.0f)
    /*
    ILResult :
        <Key & Value ??????>
        "pos_x" : IL ?????? AON??? ?????? ?????? x ??? ???????????? (???????????? ???????????? "pos_x" ??? -1.0f)
        "pos_y" : IL ?????? AON??? ?????? ?????? x ??? ???????????? (???????????? ???????????? "pos_y" ??? -1.0f)
        "gyro_from_map" : IL ?????? AON??? ????????? ???????????? ?????? ??????. --> ??? ?????? getGyroscope.setGyroCalivalue() ???????????? ????????? ???????????????. (sensorChanged ????????? ?????? ??????.)
        "status_code" : IL ?????? AON??? ?????? ??????
                        100.0f -> IL ?????? ???. ?????? ?????? ??????
                        101.0f -> IL ?????? ???. ????????? ??????
                        200.0f -> IL ??????. ?????? ??????. --> ?????? status??? ???, ?????????????????? ?????? "pos_x", "pos_y", "gyro_from_map" ???????????? initialize ????????? ???.
                        201.0f -> AON ?????? ???. (IL ?????? ?????? ????????? ?????????.)
                        202.0f -> AON ?????? ??????. (IL ?????? ?????? ????????? ?????????.) --> ?????? status??? ???, ?????????????????? ?????? "pos_x", "pos_y", "gyro_from_map" ???????????? initialize ????????? ???.
                        400.0f -> IL ?????? AON ??????. ???????????? ??????.
     */

    /*
    changedFloor_and_resetInstatLocalization(map_vector : MagneticFieldMap, map_for_instant_hand : MagneticFieldMap, gyro: Float=-1.0f)
          ???????????? : changedFloor_and_resetInstatLocalization(new_map_vector, new_map_for_instant_hand, gyro)
          ?????? : ?????? ?????? ?????? ???, ?????? ?????? ?????? ???????????? ?????? ???, ?????? ????????? ??? ????????? ????????? (???????????? ??????????????? ??????)
          ?????? : None
          ?????? : ???????????? ??? ?????????????????? ?????? ????????? ?????? ?????????.
                 ?????? ?????? ?????? ????????? ?????? ????????? ?????? ?????????????????? ???.
                        ?????? ?????? -> changedFloor_and_resetInstatLocalization() ?????? -> ????????? ?????? ??????
                 ????????? ????????? ????????? ????????? ?????? ?????? ?????? ???????????? ??????,
                 getLocation() ???????????? ????????? ?????? ??????????????? ??????????????? ?????????, ???????????? AON ?????????.
     */

    private var wifiengine : WifiMap_RSSI
    var wifidata = ""
    var wifichange = false
    var wifidataready = false
    var wifiengineready = false

    private var buildingInfo : BuildingInfo
    private var buildingInfoResult : BuilInfo? = null
    private var movingFloor : MovingFloor
    private var getInZone : Boolean = false // ???????????????, ?????????????????? ?????? ?????? ??????
    private var sendSensorToMovingFloor : Boolean = false // MovingFloor??? ?????? ?????????
    private var checkMovingFloor : Boolean = false //MovingFloor??? ?????? ?????? ?????? ?????? flag
    var floorchange : Boolean = false // html ???????????? ?????? ?????? ??????
    var infoType : String = "" //???????????????,?????????????????? ??????
    private var startfloor : Int = 0  // ?????? ??? ????????? ??????
    private lateinit var whereUserAfterMove : Array<Double> //????????????
    private lateinit var whereUserAfterMove2 : Array<Double> //????????????
    private var finalArrival : Array<Double> = arrayOf(0.0, 0.0) //?????????
    private var acontext: Context = AppforContext.myappContext()
    private val elvPressure : ElvPressure = ElvPressure(3, 0.005)
    private var pressure = FloatArray(1) //????????????
    private var moveInfrontLocate : Boolean = false
    var makeElvIn : Boolean = false
    private var zoneCheckEnd : Boolean = false
    private var moveEnd : Boolean = false
    private var makeDotLacateChange: Boolean = false
    var elvX : Double = 0.0
    var elvY : Double = 0.0
    private var linearAccZ: Float = 0f
    private var passOneFloor : Boolean = false
    private var passTwoFloor : Boolean = false
    private val pressureMoving20Average: SuminAverage = SuminAverage(20)
    private var elvPressureGradientaverage20: Double = 0.0

    private fun isReadyLocalization(event: SensorEvent) : Boolean {
        if (isSensorStabled) {
            return true
        }


        when(event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                accXMovingAverage.newData(event.values[0].toDouble())
                accYMovingAverage.newData(event.values[1].toDouble())
                accZMovingAverage.newData(event.values[2].toDouble())

                accStableCount += if (accXMovingAverage.getAvg() in -0.3..0.3 && accYMovingAverage.getAvg() in -0.3..0.3 && accZMovingAverage.getAvg() in -0.3..0.3) -1 else 1

                accStableCount = if (accStableCount > 50) 50 else accStableCount
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magStableCount--
            }
        }

        isSensorStabled = accStableCount<0 && magStableCount<=0
        return isSensorStabled
    }

    fun sensorChanged(event: SensorEvent?) : Array<String> {
        if ((event ?: false).let { isReadyLocalization(event!!) }) {
            when (event!!.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accMatrix = event.values.clone()
                }
                Sensor.TYPE_LIGHT -> {
                    heroPDR.setLight(event.values[0])
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magMatrix = event.values.clone()
                    caliVector = vectorCalibration.calibrate(accMatrix, magMatrix, gyro_value_map_collection)

                    // ??? ??????????????? ?????? instant ??????????????? ???
                    if (first_sampling && caliVector[0]!=0.0 && caliVector[1]!=0.0 && caliVector[2]!=0.0) { // ?????? if?????? ??? ????????????. ????????? ?????? ???????????? ?????? ?????? ???????????????, ????????? ????????? ???????????? ?????????????????? ??????.
                        //////WiFi Engine
                        wifi_range = wifiengine.vectorcompare(wifidata)

                        instantLocalization.getLocation(caliVector, caliVector, 0.0, gyro_value_map_collection, gyro_value_reset_direction, devicePosture, PFResult, wifi_range)
                        first_sampling = false
                    }

                    //????????? ??? ?????? ????????? ?????? MovingFloor??? ???????????? ???. ????????? ????????? ????????? ??????.


                }
                Sensor.TYPE_GYROSCOPE -> {
                    getGyroscope.calc_gyro_value(event)
                    gyro_value_map_collection = getGyroscope.from_map_collection()
                    gyro_value_reset_direction = getGyroscope.from_reset_direction()
                    gyro_constant_from_app_start_to_map_collection = getGyroscope.from_app_start_to_map_collection()

                    heroPDR.setDirection(gyro_value_map_collection.toDouble())
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    heroPDR.setQuaternion(event.values.clone())
                }
                Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    var roVecMatrix = event.values.clone()
                    if (roVecMatrix.isNotEmpty()) {
                        vectorCalibration.setQuaternion(roVecMatrix)
                    }
                }
                Sensor.TYPE_PRESSURE -> {
                    pressure = event.values.clone()

                    if(sendSensorToMovingFloor) { ///??????
                        movingFloor.getPressure(pressure[0].toDouble())
                    }

                    if(moveInfrontLocate) {
                        pressureMoving20Average.newData(pressure[0].toDouble())
                        elvPressureGradientaverage20 = elvPressure.getPressureGradient(pressureMoving20Average.getAvg())

                        Log.d("suminInfoTye",infoType)
                        Log.d("suminGradient", (elvPressureGradientaverage20*1000).toString())
                        if((elvPressureGradientaverage20*1000 < -3 || elvPressureGradientaverage20*1000 > 3 ) && !getInZone) {
                            Toast.makeText(acontext, "???????????? ?????????????????? ?????????????????????. ", Toast.LENGTH_SHORT).show()

                            if (infoType == "EL1Front" && startfloor == 0) { //?????? 1????????? ??????
                                elvX = 170.0
                                elvY = 18.0
                            }

                            if (infoType == "EL2Front" && startfloor == 0) {
                                elvX = 170.0
                                elvY = 43.0
                            }

                            if (infoType == "EL1Front" && startfloor != 0) { // ??? ) 1????????? ??????
                                elvX = 83.0
                                elvY = 32.0
                            }

                            if (infoType == "EL2Front" && startfloor != 0) {
                                elvX = 83.0
                                elvY = 56.0
                            }

                            if (infoType == "EL2Front2" && startfloor == 0) {
                                elvX = 170.0
                                elvY = 43.0
                            }

                            if (infoType == "EL2Front2" && startfloor != 0) {
                                elvX = 83.0
                                elvY = 56.0

                            }

                            movingFloor.getCondition(infoType, startfloor , true )

                            makeElvIn = true // ?????? ?????? ????????? ??????.
                            getInZone = true // ?????? ????????? ??????
                            sendSensorToMovingFloor = true // ?????? ?????? ??????
                            checkMovingFloor = true // ?????? ???????????? ??? ??????
                            moveInfrontLocate = false // ?????? ????????? ?????? ??????
                        }
                    }

                    if(makeDotLacateChange) {

                        if (infoType == "EL1" && startfloor == 0) { //?????? 1????????? ??????
                            elvX = 170.0
                            elvY = 18.0
                            makeElvIn = true
                        }

                        if (infoType == "EL2" && startfloor == 0) {
                            elvX = 170.0
                            elvY = 43.0
                            makeElvIn = true
                        }

                        if (infoType == "EL1" && startfloor != 0) { // ??? ) ?????? 1????????? ??????
                            elvX = 83.0
                            elvY = 32.0
                            makeElvIn = true
                        }

                        if (infoType == "EL2" && startfloor != 0) {
                            elvX = 83.0
                            elvY = 56.0
                            makeElvIn = true
                        }

                        if(infoType == "EL3" && startfloor == 0) { //?????? 1???
                            elvX = 163.0
                            elvY = 1280.0
                            makeElvIn = true
                        }

                        if(infoType == "EL4" && startfloor == 0) { //?????? 1???
                            elvX = 163.0
                            elvY = 1340.0
                            makeElvIn = true
                        }

                        if(infoType == "EL3" && startfloor != 0) {
                            elvX = 70.0
                            elvY = 13.0
                            makeElvIn = true
                        }

                        if(infoType == "EL4" && startfloor != 0) {
                            elvX = 70.0
                            elvY = 65.0
                            makeElvIn = true
                        }

                        makeDotLacateChange = false
                    }

                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    accXMovingAverage.newData(event.values[0].toDouble())
                    accYMovingAverage.newData(event.values[1].toDouble())
                    accZMovingAverage.newData(event.values[2].toDouble())

                    linearAccZ = event.values[2]
                    if(sendSensorToMovingFloor) {///??????
                        movingFloor.getLinerAccZ(linearAccZ.toDouble())
                    }

                    if (heroPDR.isStep(arrayOf(accXMovingAverage.getAvg(), accYMovingAverage.getAvg(), accZMovingAverage.getAvg()), caliVector)) {
                        pdrResult = heroPDR.getStatus()
                        devicePosture = pdrResult.devicePosture
                        stepType = pdrResult.stepType
                        stepCount = pdrResult.totalStepCount
                        userDir = pdrResult.direction

                        ////WiFI Engine

                        caliVector_AON = vectorCalibration.calibrate(
                            accMatrix,
                            magMatrix,
                            gyro_value_reset_direction
                        )

                        //////Instant Localization
                        if(!moveEnd) {
                            CoroutineScope(Dispatchers.Default).async {
                                wifi_range = wifiengine.vectorcompare(wifidata)

                                ILResult = async {
                                    instantLocalization.getLocation(
                                        caliVector_AON,
                                        caliVector,
                                        pdrResult.stepLength + 0.07,
                                        gyro_value_map_collection,
                                        gyro_value_reset_direction,
                                        devicePosture,
                                        PFResult,
                                        wifi_range
                                    )
                                }.await()
                            }
                        }
                        if(!moveEnd) {
                            if ((ILResult["status_code"] == 200.0f) || (ILResult["status_code"] == 202.0f)) {
                                particleFilter = ParticleFilter(
                                    resourceDataManager.magneticFieldMap,
                                    100,
                                    round(ILResult["pos_x"]!!).toInt(),
                                    round(ILResult["pos_y"]!!).toInt(),
                                    10
                                )
                                PFResult = arrayOf(
                                    ILResult["pos_x"]!!.toDouble(),
                                    ILResult["pos_y"]!!.toDouble()
                                )
                                getGyroscope.setGyroCalivalue(ILResult["gyro_from_map"]!!)
                                getGyroscope.gyro_reset()
                                particleOn = true
                                returnState = "?????? ??????"
                            } else if ((particleOn && (ILResult["status_code"]!! > 200.0f))) {
                                PFResult = particleFilter.step(
                                    caliVector,
                                    gyro_value_map_collection.toDouble(),
                                    pdrResult.stepLength + 0.07
                                )
                            }
                        } else {
                            PFResult = particleFilter.step(caliVector, gyro_value_map_collection.toDouble(), pdrResult.stepLength + 0.07
                            )
                        }


                        if (particleOn) {
                            // -----------------------------------Sumin---------------------------------------------------------
                            if (isFloorChange) {
                                buildingInfoResult = buildingInfo.search(PFResult[0], PFResult[1])

                                if(buildingInfoResult != null) {

                                    Log.d("suminBuilding",buildingInfoResult!!.type)
                                    if(!getInZone && (buildingInfoResult!!.type == "EL1Front" || buildingInfoResult!!.type == "EL2Front" || buildingInfoResult!!.type == "EL2Front2")) {

                                        infoType = buildingInfoResult!!.type
                                        whereUserAfterMove = buildingInfoResult?.arrival!!
                                        whereUserAfterMove2 = buildingInfoResult?.arrival2!!

                                        moveInfrontLocate = true
                                    }

                                    if (!getInZone && (buildingInfoResult!!.type != "EL1Front") && (buildingInfoResult!!.type != "EL2Front") && (buildingInfoResult!!.type != "EL2Front2")) {

                                        moveInfrontLocate = false
                                        infoType = buildingInfoResult!!.type
                                        whereUserAfterMove = buildingInfoResult?.arrival!!
                                        whereUserAfterMove2 = buildingInfoResult?.arrival2!!
                                        makeDotLacateChange = true
                                        movingFloor.getCondition(infoType, startfloor, false)

                                        Toast.makeText(acontext, infoType + "?????????????????? ?????????????????????. ", Toast.LENGTH_SHORT).show()

                                        sendSensorToMovingFloor = true
                                        checkMovingFloor = true
                                        getInZone = true
                                    }
                                }


                                if(zoneCheckEnd && buildingInfoResult == null) {

                                    if(infoType == "EL1Front") {
                                        infoType = "EL1"
                                    }
                                    if(infoType == "EL2Front") {
                                        infoType = "EL2"
                                    }

                                    Toast.makeText(acontext, infoType + "????????????????????? ?????????????????????. ", Toast.LENGTH_SHORT).show()
                                    zoneCheckEnd = false
                                    getInZone = false
                                }


                            if (checkMovingFloor) {

                                if (movingFloor.isArrival) {
                                    floorchange = true
                                    startfloor = movingFloor.getFloor() //?????????

                                    if(startfloor != 0) {
                                        finalArrival[0] = whereUserAfterMove2[0] //?????? ?????? x
                                        finalArrival[1] = whereUserAfterMove2[1] //?????? ?????? y
                                    }

                                    if(startfloor == 0) {
                                        finalArrival[0] = whereUserAfterMove[0] //?????? ?????? x
                                        finalArrival[1] = whereUserAfterMove[1] //?????? ?????? y
                                    }


                                    if(startfloor != 0 && (infoType == "EL1" || infoType == "EL2"|| infoType == "EL1Front" || infoType == "EL2Front")) {
                                        mapAfterFloorChange = MagneticFieldMap(acontext.resources.openRawResource(R.raw.hana1floor_first))
                                        mapInstantAfterFloorChange = MagneticFieldMap(acontext.resources.openRawResource(R.raw.hana1floor_first_forinstant3))
                                        buildingInfo = BuildingInfo(acontext.resources.openRawResource(R.raw.build_map_first))
                                    }

                                    if (startfloor != 0 && infoType == "EL3" || infoType == "EL4") {
                                        mapAfterFloorChange = MagneticFieldMap(acontext.resources.openRawResource(R.raw.hana1floor_second))
                                        mapInstantAfterFloorChange = MagneticFieldMap(acontext.resources.openRawResource(R.raw.hana1floor_second_forinstant3))
                                        buildingInfo = BuildingInfo(acontext.resources.openRawResource(R.raw.build_map_second))
                                    }

                                    if( startfloor == 0 ) {
                                        mapAfterFloorChange = MagneticFieldMap(acontext.resources.openRawResource(R.raw.hana_b1floor))
                                        mapInstantAfterFloorChange = MagneticFieldMap(acontext.resources.openRawResource(R.raw.hana_b1floor_for_instant_3))
                                        buildingInfo = BuildingInfo(acontext.resources.openRawResource(R.raw.build_map_b1floor))
                                    }

                                    //instantLocalization.changedFloor_and_resetInstatLocalization(mapAfterFloorChange, mapInstantAfterFloorChange, gyro_value_reset_direction)
                                    particleFilter = ParticleFilter(mapAfterFloorChange, 100, round(finalArrival[0]).toInt(), round(finalArrival[1]).toInt(), 10)

                                    checkMovingFloor = false
                                    sendSensorToMovingFloor = false
                                    passOneFloor = false
                                    passTwoFloor = false
                                    moveEnd = true
                                    movingFloor.isArrival = false
                                    zoneCheckEnd = true
                                }
                            }
                        }

                            // -------------------------------------------------------------------------------------------------
                        }
                    } else {
                        devicePosture = heroPDR.getPosture()
                        stepType = heroPDR.getStepType()
                    }
                }
            }
        } else {
            return arrayOf("The sensors is not ready yet", "The sensors is not ready yet", "The sensors is not ready yet", "-1", "The sensors is not ready yet", "The sensors is not ready yet")
        }
        returnGyro = gyro_value_map_collection.toString()
        returnIns = ILResult["status_code"].toString()
        returnX = PFResult[0].toString()
        returnY = PFResult[1].toString()
        return arrayOf(returnGyro, returnState, returnIns, stepCount.toString(), returnX, returnY , "$startfloor")
    }

    fun getPose(): String {
        return poseTypes[devicePosture]
    }

    fun getType() : String {
        return stepTypes[stepType]
    }
}
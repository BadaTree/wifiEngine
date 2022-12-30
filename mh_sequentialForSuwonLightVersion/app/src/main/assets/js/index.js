var pckName = 'hana'
var cnt = 0;
var positions = [];
var dot_num = 100;
var pos_x, pos_y;
var mode = 0;
const X_B1 = 170;
const Y_B1 = 550;
const WIDTH_B1 = 1580;

const X_B2 = 63;    //감소 시 좌측 이동
const Y_B2 = 383;   //감소 시 아래로 이동
const WIDTH_B2 = 1300;

const X_B3_BO = 165;  //증가 시 이미지 좌측이동
const Y_B3_BO = 50;   //증가 시 이미지 아래로
const WIDTH_B3 = 2170;

const X_B3_KO = 230;
const Y_B3_KO = 36;
const WIDTH_B4 = 2170;

var corrX = X_B1
var corrY = Y_B1



for (var j=0; j<2000; j++) {
    var plusUI2 = document.createElement("div");
    plusUI2.style = "position:absolute; z-index:2; left:"+(pos_x)+"px; top:"+(pos_y)+"px; display:block;";
    plusUI2.id = "children" + String(j);
    plusUI2.innerHTML = '<font style="color:#ff0000;font-size:14px" >⦿</font>';
    document.getElementById("map_area").appendChild(plusUI2);
}

// var arrowUI = document.createElement("img");
// arrowUI.src = "./images/ui/arrow.png"
// arrowUI.style = "position:absolute; z-index:3; left:"+(-10000)+"px; top:"+(-10000)+"px; display:block;";
// arrowUI.id = "arrow";
// arrowUI.width = "30"
// document.getElementById("map_area").appendChild(arrowUI);

var center_x = 800;
var center_y = 600;
var last_x = 0;
var last_y = 0;
var last_angle = 0;
var is_first_angle = true;

import data from '../images/file_list.json' assert {type: "json"}
const dataList = Object.keys(data)
var changeFloor = function(idx){
    let img = new Image();
    img.src = `./images/map/${pckName}_${dataList[idx]}.png`;
    let left = (1000 - img.width)/2
    let top = (1000 - img.width)/2
    document.getElementById("map_img").src = img.src;
    document.getElementById("map_area").style=`position:absolute; z-index:1;left: 0px; top: 1000px; overflow:scroll; transition: all 0.1s; transform: rotate(270deg)`
}

function initWebview(pckName){
    window.pckName = pckName
    changeFloor(0)
}

function show_all_children (pos_list) {
    var num = 0
    for (var pos of pos_list) {
        // var plusUI = document.createElement("div");
        var plusUI = document.getElementById("children" + String(num));
        plusUI.style = "position:absolute; z-index:2; left:"+(pos[1] - 10)+"px; top:"+(pos[0] - 15)+"px; display:block;";
        // plusUI.className = "children";
        // plusUI.innerHTML = '<font style="color:#0000ff;font-size:7px" >⦿</font>';
        // document.getElementById("map_area").appendChild(plusUI);
        num += 1
    }

    for (var i=num; i<2000; i++) {
        var plusUI2 = document.getElementById("children" + String(i));
        plusUI2.style = "position:absolute; z-index:2; left:"+(-100)+"px; top:"+(-10000)+"px; display:block;";
    }

}

function androidBridge (xPosition2, yPosition2, pose="On Hand") { // DEVICE
    positions.pop(); // 배열의 가장 마지막값 삭제
    positions.unshift([xPosition2, yPosition2]); // 배열의 가장 처음에 값 추가
    for (var i = 0; i < dot_num; i++) {

// <!--            var plusUI = document.getElementById("dot" + String(i));-->
// <!--            if (i === 0) {-->
// <!--                if (pose === "On Hand") {-->
// <!--                    plusUI.src = "./images/dot.png"-->
// <!--                } else if (pose === "In Pocket") {-->
// <!--                    plusUI.src = "./images/dotb.png"-->
// <!--                } else {-->
// <!--                    plusUI.src = "./images/dotg.png"-->
// <!--                }-->
// <!--            } else {-->
// <!--                plusUI.src = "./images/dot.png"-->
// <!--            }-->
// <!--            plusUI.style = "position:absolute; z-index:2; left:" + (positions[i][1] + 40) + "px; top:" + (positions[i][0] + 313) + "px; display:block; opacity:" + (1.0 - (i / dot_num)) + ";";-->
// <!--            // document.getElementById("map_area").appendChild(plusUI);-->
    }

    // 점 따라 맵 이미지 움직이기
    //document.getElementById("map_area").appendChild(plusUI);
    //var elem = document.getElementById("map_area");
    //elem.style.left = center_x - yPosition2 + "px";
    //elem.style.top = center_y - xPosition2 + "px";
}

function androidBridge2 (xPosition2, yPosition2) { // DEVICE

    var plusUI = document.createElement("div");
    plusUI.style = "position:absolute; z-index:2; left:"+(yPosition2+43)+"px; top:"+(xPosition2+310)+"px; display:block;";
    plusUI.id = "location_area";
    plusUI.innerHTML = '<font color="#0000ff" size=1>●</font>';
    cnt += 1;
    if (cnt == 28) {
        cnt = 0;
    }
    document.getElementById("map_area").appendChild(plusUI);
    var elem = document.getElementById("map_area");
    elem.style.left = center_x - yPosition2 + "px";
    elem.style.top = center_y - xPosition2 + "px";

    last_x = xPosition2;
    last_y = yPosition2;

}

function androidBridge3 (xPosition2, yPosition2) { // DEVICE

    var plusUI = document.createElement("div");
    plusUI.style = "position:absolute; z-index:2; left:"+(yPosition2+43)+"px; top:"+(xPosition2+310)+"px; display:block;";
    plusUI.id = "location_area";
    plusUI.innerHTML = '<font color="#00ff00" size=1>●</font>';
    cnt += 1;
    if (cnt == 28) {
        cnt = 0;
    }
    document.getElementById("map_area").appendChild(plusUI);
    var elem = document.getElementById("map_area");
    elem.style.left = center_x - yPosition2 + "px";
    elem.style.top = center_y - xPosition2 + "px";

    last_x = xPosition2;
    last_y = yPosition2;

}

function image_rotation(angle) {
    if (is_first_angle) {
        last_angle = angle;
        is_first_angle = false;
    }

    var elem = document.getElementById("map_area");
    elem.style.transformOrigin = (last_y+267) + "px " + (last_x+312) + "px"
    if ((last_angle - angle) > 180) {
        angle += 360
    }
    else if ((last_angle - angle) < -180) {
        angle -= 360
    }
    var result_angle = 270-angle
    elem.style.transform = "rotate(" + result_angle + "deg)"
    last_angle = angle
}

function arrow_rotation(angle) {
    var arrow = document.getElementById("arrow")
    arrow.style.left = (positions[0][1] + 51) + "px"
    arrow.style.top = (positions[0][0] + 305) + "px"
    arrow.style.transform = "rotate(" + angle + "deg)"

}

function remove_children (){
    const elements = document.getElementsByClassName("children");
    while(elements.length > 0){
        elements[0].parentNode.removeChild(elements[0]);
    }
}


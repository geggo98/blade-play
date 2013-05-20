/**
 * Created with IntelliJ IDEA.
 * User: sschwets
 * Date: 09.02.13
 * Time: 19:55
 * To change this template use File | Settings | File Templates.
 */

var IO;
$(el).trigger("/heatmap",[{name: "test"}]);
$(el).on("/heatmap", function(event){});
$(el).off("/heatmap");

var IOCometStreaming;
var IOWebSocket;
var IOPolling;


var Maps = (function(maps) {

	return {

	};
}(google.maps))

var GpsTracker = (function() {
	return {

	}
}())


function initializeSocket(socketUrl) {

	var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;
	var trackerSocket = new WS(socketUrl);

	trackerSocket.onmessage = function(event) {
		var data = JSON.parse(event.data)

		// Handle errors
		if(data.error) {
			trackerSocket.close()
			showError(data.error)
			return
		} else {
			$("#onChat").show()
		}
	}

	var sendMessage = function() {
		trackerSocket.send(JSON.stringify(
			{text: $("#talk").val()}
		))
	}

	return trackerSocket
}


function initializeMap() {
	var centerLatLng = new google.maps.LatLng(48.13248, 11.54427);
	var mapOptions = {
		zoom: 13,
		center: centerLatLng,
		mapTypeId: google.maps.MapTypeId.ROADMAP
	};

	var map = new google.maps.Map(document.getElementById('map_canvas'), mapOptions);

	var flightPlanCoordinates = [
		new google.maps.LatLng(48.13238, 11.54437),
		new google.maps.LatLng(48.13228, 11.54437),
		new google.maps.LatLng(48.13148, 11.54327),
		new google.maps.LatLng(48.13348, 11.54227)
	];
	var flightPath = new google.maps.Polyline({
		path: flightPlanCoordinates,
		strokeColor: '#FF0000',
		strokeOpacity: 0.5,
		strokeWeight: 4
	});

	flightPath.setMap(map);
	return map;
}

function initializeTracker(socket, map) {
	if (navigator.geolocation) {
		var watchId = navigator.geolocation.watchPosition(function(position) {
				console.log(position.coords.latitude);
				console.log(position.coords.longitude);
				console.log(position.coords.accuracy);
				var posCircle=new google.maps.Circle({
					strokeColor: '#FF0000',
					strokeOpacity: 0.8,
					strokeWeight: 2,
					fillColor: '#FF0000',
					fillOpacity: 0.35,
					map: map,
					center: new google.maps.LatLng(position.coords.latitude, position.coords.longitude),
					radius: position.coords.accuracy
				})
			}, function(err){showError("Fehler beim Zugriff auf GPS: "+err.toString())},
			{enableHighAccuracy:true, interval_ms:5000, maximumAge:30000});
	} else{
		showError("Kann nicht auf GPS Daten zugreifen.")
	}
}

$(function(){
	var socket=initializeSocket();
	var map=initializeMap();
	$("#start_gps").click(function(event){
		initializeTracker(socket, map);
	})
});

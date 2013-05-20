/**
 * Created with IntelliJ IDEA.
 * User: sschwets
 * Date: 19.02.13
 * Time: 16:34
 * To change this template use File | Settings | File Templates.
 */

define(
	["./io/comet_streaming.js", "./io/polling.js", "./io/web_socket.js", "./io/pub_sub.js",
	"./error_message.js", "./timer.js"],
	function (comet, polling, webSocket, pubSub, errorMessage, timer) {
		var receivedJsonMessageFromServerChannel = new pubSub.Channel("/io/received_json_message");
		var sendJsonMessageToServer = new pubSub.Channel("/io/send_json_message");

		var lastMessageTimestamp = timer.getTime();
		var lastWebSocketMessageTimestamp = lastMessageTimestamp;
		var lastCometMessageTimestamp = lastMessageTimestamp;

		var currentIoMethod=webSocket.id;

		var checkIoInterval=5500;
		var timeoutWebSocket=10000;
		var timeoutComet=15000;

		var timerCheckIo;

		var coordinateIoMethods = function(jsonMessage) {

			// Compute which events have occurred

			var currentMessageTimestamp = timer.getTime();

			var eventCometAlive=false;
			var eventWebSocketAlive=false;

			if (jsonMessage && jsonMessage.sender) {
				lastMessageTimestamp=currentMessageTimestamp;
				var senderId=jsonMessage.sender.id;
				if (senderId==webSocket.id) {
					eventWebSocketAlive=true;
					lastWebSocketMessageTimestamp=currentMessageTimestamp;
				}else if (senderId==comet.id) {
					eventCometAlive=true;
					lastCometMessageTimestamp=currentMessageTimestamp;
				}
			}

			var eventCometTimeout=(lastCometMessageTimestamp-currentMessageTimestamp >= timeoutComet);
			var eventWebSocketTimeout=(lastWebSocketMessageTimestamp-currentMessageTimestamp >= timeoutWebSocket);

			var newIoMethod=currentIoMethod;

			// Compute new state

			if (currentIoMethod!=webSocket.id && eventWebSocketAlive  && webSocket.available()) {
				newIoMethod=webSocket.id;
			}
			if (currentIoMethod==webSocket.id && eventWebSocketTimeout) {
				if (eventCometTimeout) {
					newIoMethod=polling.id;
				}else{
					newIoMethod=comet.id;
				}
			}

			if (currentIoMethod==comet.id && eventCometTimeout) {
				if (eventWebSocketAlive && webSocket.available()) {
					newIoMethod=webSocket.id;
				}else{
					newIoMethod=polling.id;
				}
			}

			if (currentIoMethod==polling.id && eventCometAlive) {
				newIoMethod=comet.id;
			}

			// Switch to new state
			if (currentIoMethod!=newIoMethod) {
				if (currentIoMethod==comet.id) {
					comet.reset();
				}

				if (newIoMethod==webSocket.id) {
					webSocket.enableSend();
					polling.disableAll();
					comet.disableAll();
				}

				if (newIoMethod==comet.id) {
					webSocket.disableSend();
					polling.enableSend();
					polling.disableReceive();
					comet.enableReceive();
				}

				if (newIoMethod==polling.id) {
					webSocket.disableSend();
					polling.enableSend();
					polling.disableReceive();
				}
			}
		};

		var stopAll = function () {
			webSocket.disableAll();
			comet.disableAll();
			polling.disableAll();
			timerCheckIo.cancel();
		};

		var dispatchErrors = function(jsonMessage) {
			if (jsonMessage) {
				if (jsonMessage.error) {
					errorMessage.showError(jsonMessage.error);
				}

				if (jsonMessage.fatal) {
					errorMessage.showError(jsonMessage.fatal);
					stopAll();
				}
			}
		}

		var sendJsonObjectToServer = function(obj) {
			sendJsonMessageToServer.publish(obj);
		};

		var init = function (cometUrl, pollingUrl, webSocketUrl) {
			comet.init(cometUrl, receivedJsonMessageFromServerChannel);
			polling.init(pollingUrl, receivedJsonMessageFromServerChannel);
			webSocket.init(webSocketUrl, receivedJsonMessageFromServerChannel);

			receivedJsonMessageFromServerChannel.subscribe(coordinateIoMethods);
			receivedJsonMessageFromServerChannel.subscribe(dispatchErrors);
			timerCheckIo=new timer.TimerPeriodic(coordinateIoMethods,checkIoInterval)
		};

		return {
			init: init,
			stopAll: stopAll,
			sendJsonObjectToServer: sendJsonObjectToServer,
			receivedJsonMessageFromServerChannel: receivedJsonMessageFromServerChannel
		};
	}
);
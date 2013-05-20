/**
 * Created with IntelliJ IDEA.
 * User: sschwets
 * Date: 19.02.13
 * Time: 16:49
 * To change this template use File | Settings | File Templates.
 *
 * see [[https://gist.github.com/addyosmani/1321768]]
 */
define(["jquery"], function($) {
	var el=$({});
	var id= 0;

	function Channel(name) {
		this.name=name+"-"+id.toString();
		id++;
	}

	Channel.prototype.subscribe=function(callBack) {
		el.on.apply(this.name, callBack);
	}

	Channel.prototype.unSubscribe=function(callBack) {
		el.off.apply(this.name, callBack);
	}

	Channel.prototype.publish=function(message) {
		el.trigger.apply(this.name, message);
	}

	return {
		Channel: Channel
	};
});

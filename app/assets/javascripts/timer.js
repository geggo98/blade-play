/**
 * Created with IntelliJ IDEA.
 * User: sschwets
 * Date: 20.02.13
 * Time: 23:17
 * To change this template use File | Settings | File Templates.
 */

define(["underscore"],function(){
	var periodicTimers=new Array();
	var oneShotTimers=new Array();

	var fakeTime=undefined;

	var getTime=function() {
		if (fakeTime) {
			return fakeTime;
		}else{
			return new Date().getTime();
		}
	}

	var setFakeTime = function(_time) {
		fakeTime=_time;
	}

	var clearFakeTime = function() {
		fakeTime=undefined;
	}

	var fakeFirePeriodic = function() {
		var timers=periodicTimers;
		timers.forEach(function(el){el.fakeFire()})
	}

	var fakeFireOneShot = function() {
		var timers=oneShotTimers;
		timers.forEach(function(el){el.fakeFire()})
	}


	function TimerOneShot(callback, interval_ms) {
		this.interval_ms = interval_ms;
		this.callback = callback;

		if (fakeTime) {
			this.handle=undefined;
		}else{
			this.handle = window.setTimeout(callback, interval_ms);
		}
	}

	TimerOneShot.prototype.cancel=function(){
		if (this.handle) {
			window.clearTimeout(this.handle);
			this.handle=undefined;
		}
		oneShotTimers=oneShotTimers.filter(function(el){ return el !== this});
	}

	TimerOneShot.prototype.fakeFire=function(){
		this.callback();
	}

	function TimerPeriodic(callback, interval_ms) {
		this.interval_ms = interval_ms;
		this.callback = callback;

		if (fakeTime) {
			this.handle=undefined;
		}else{
			this.handle = window.setInterval(callback, interval_ms);
		}
	}

	TimerPeriodic.prototype.cancel=function(){
		if (this.handle) {
			window.clearTimeout(this.handle);
			this.handle=undefined;
		}
		periodicTimers=periodicTimers.filter(function(el){ return el !== this});
	}

	TimerPeriodic.prototype.fakeFire=function(){
		this.callback();
		periodicTimers=periodicTimers.filter(function(el){ return el !== this});
	}

	return {
		setFakeTime: setFakeTime,
		clearFakeTime: clearFakeTime,
		fakeFireOneShot: fakeFireOneShot,
		fakeFirePeriodic: fakeFirePeriodic,
		getTime: getTime,
		TimerOneShot: TimerOneShot,
		TimerPeriodic: TimerPeriodic
	};
})
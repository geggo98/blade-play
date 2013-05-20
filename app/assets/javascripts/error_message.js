/**
 * Created with IntelliJ IDEA.
 * User: sschwets
 * Date: 10.02.13
 * Time: 11:40
 * To change this template use File | Settings | File Templates.
 */

var Error = (function($) {
	function showError(msg) {
		console.log(["[Error]", msg]);
		var template = $("#onError");
		var errorBox = template.clone();
		errorBox.attr('id', 'error_msg-' + (new Date().getTime().toString()));
		errorBox.find("span").text(msg);
		template.after(errorBox);
		errorBox.show('slow');
		return errorBox;
	}

	return {
		showError: showError
	}
}(jQuery));


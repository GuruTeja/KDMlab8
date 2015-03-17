var base_url = window.location.origin;

function perform() {
	var sentence = $("#sentence");
	var url = base_url + "/api/hello/KE/"+ encodeURI(sentence.val());
	performService(url);
}

function performService(url) {
	console.log(url);
	var resultDiv = $("#result");
	$.get(url, function(data) {
		console.log(data);
		resultDiv.text(JSON.stringify(data));
	}).fail(function(data) {
		console.log(data);
		alert(data);
	});
}
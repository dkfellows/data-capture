/** Unbreak what the JSON processing does with arrays */
function dejson(val) {
	if (val === undefined)
		return [];
	else if (val.constructor !== Array)
		return [ val ];
	return val;
}
/** How to add an option */
function addOption(select, id, value, name) {
	var opt = "<option></option>";
	if (id !== undefined)
		opt = "<option id='" + id + "'></option>";
	select.append($(opt).val(value).text(name));
}
/** How to request some JSON asynchronously */
function getJSON(u, done, errors) {
	if (errors === undefined)
		errors = function(jqXHR, textStatus, errorThrown) {
			alert(errorThrown);
		};
	$.ajax({
		type : "GET",
		url : u,
		async : true,
		accept : "application/json",
		success : done,
		error : errors
	});
}
/** How to send a POST of JSON asynchronously. */
function postJSON(u, object, done, errors) {
	if (errors === undefined)
		errors = function(jqXHR, textStatus, errorThrown) {
			alert(errorThrown);
		};
	$.ajax({
		type : "POST",
		url : u,
		async : true,// TODO check this
		cache : false,
		contentType : "application/json",
		accept : "application/json",
		data : JSON.stringify(object),
		success : done,
		error : errors
	});
}
/** Generate a human-readable description of a date */
function timeSince(date) {
	var seconds = Math.floor((new Date() - date) / 1000);

	var interval = Math.floor(seconds / 31536000);

	if (interval > 1) {
		return interval + " years ago";
	}
	if (interval == 1) {
		interval = Math.floor(seconds / 2592000) - 12;
		if (interval > 1)
			return "1y " + interval + "m ago";
	}
	interval = Math.floor(seconds / 2592000);
	if (interval > 1) {
		return interval + " months ago";
	}
	if (interval == 1) {
		interval = Math.floor(seconds / 86400) - 31;
		if (interval > 1)
			return "1m " + interval + "d ago";
	}
	interval = Math.floor(seconds / 86400);
	if (interval > 1) {
		return interval + " days ago";
	}
	if (interval == 1) {
		interval = Math.floor(seconds / 3600) - 24;
		if (interval > 1)
			return "1d " + interval + "h ago";
	}
	interval = Math.floor(seconds / 3600);
	if (interval > 1) {
		return interval + " hours ago";
	}
	if (interval == 1) {
		interval = Math.floor(seconds / 60) - 60;
		if (interval > 1)
			return "1h " + interval + "m ago";
	}
	interval = Math.floor(seconds / 60);
	if (interval > 1) {
		return interval + " mins ago";
	}
	return Math.floor(seconds) + " secs ago";
}
/** How exactly to render a timestamp in HTML */
function setTimestamp(cell, timestamp) {
	if (timestamp !== undefined) {
		if (cell.children().length == 0)
			cell.append($("<span>").attr("title", timestamp));
		var t = new Date(Date.parse(timestamp));
		cell.children().text(timeSince(t));
	}
}
/** How exactly to set the progress bar and its label */
function setProgress(progress, factor) {
	var label = progress.find(".progress-label");
	if (factor === undefined) {
		progress.progressbar("option", {
			value : false
		});
		label.text("Init...");
		return false;
	} else {
		var val = Math.floor(factor * 100);
		progress.progressbar("option", {
			value : val
		});
		label.text(val == 100 ? "Done" : val + "%");
		return val == 100;
	}
}

/**
 * Create a row of the task table
 */
function addTaskRow(table, task) {
	/** Create a delete button */
	function delbutn(id) {
		return $(
				"<button id='del_" + id
						+ "' title='Delete this archiving task.'>Del</button>")
				.button({
					icons : {
						primary : "ui-icon-trash"
					},
					text : false
				});
	}
	/** Create a progress bar */
	function progressbar(id) {
		return $("<div>").attr("id", "progress_" + id).append(
				$("<div class='progress-label'>")).progressbar({
			value : false
		});
	}

	var id = "#" + task.id;
	var row = $(id);
	if (row.length != 0)
		return row;
	row = $("<tr id='" + task.id + "' class='taskrow'>");
	/** Create a cell in the task table row */
	function cell(id) {
		var td = $("<td>");
		row.append(td);
		return td;
	}
	/** Create a cell that links to a named thing */
	function linkcell(thing) {
		cell().append($("<a>").attr("href", thing.url).text(thing.name));
	}
	/** Create a cell containing a timestamp */
	function datecell(timestamp) {
		var c = cell();
		setTimestamp(c, timestamp);
		return c;
	}
	var t;
	cell().text(task.id);
	linkcell(task.submitter);
	linkcell(task.assay);
	datecell(task["start-time"]).attr("id", "start_" + task.id);
	var progress = progressbar(task.id);
	setProgress(progress, task.progress);
	cell().append(progress);
	datecell(task["end-time"]).attr("id", "end_" + task.id);
	var asset = task["created-asset"];
	linkcell({
		url : asset === undefined ? "" : asset,
		name : asset === undefined ? "" : "Asset"
	}).attr("id", "asset_" + id);
	cell().append(delbutn(task.id).click(function() {
		var u = $("#apiTasks")[0].href + "/" + task.id;
		$.ajax({
			type : "DELETE",
			url : u,
			async : true,
			accept : "application/json",
			success : function(a, b, c) {
				$("#" + task.id).remove();
			},
			error : function(a, b, c) {
				console.log("delete fail", a, b, c);
				alert("failed to delete task");
			}
		});
	}));
	table.append(row);
	return row;
}

/** How to update the progress of a task in a row of the task table */
function updateProgress() {
	function updateRow(id) {
		var progress = $("#progress_" + id);
		var start = $("#start_" + id);
		var end = $("#end_" + id);
		var url = $("#apiTasks")[0].href + "/" + id;
		getJSON(url, function(task) {
			setTimestamp(start, task["start-time"]);
			setProgress(progress, task.progress);
			setTimestamp(end, task["end-time"]);
		}, function(a) {
			$("#" + id).remove();
		});
		var asset = task["created-asset"];
		if (asset !== undefined)
			$("#asset_" + id + " a").attr("href", asset).text("Asset");
	}
	return $(".taskrow").each(function() {
		updateRow($(this).attr("id"));
	});
}

/** Start everything going on page load */
$(function() {
	var theUser, theAssay, theDir;

	$("#users").selectmenu({
		change : function(e, d) {
			theUser = d.item.value;
		}
	}).selectmenu("menuWidget").addClass("overflow");
	$("#assays").selectmenu({
		change : function(e, d) {
			theAssay = d.item.value;
		}
	}).selectmenu("menuWidget").addClass("overflow");
	$("#dirs").selectmenu({
		change : function(e, d) {
			theDir = d.item.value;
		}
	}).selectmenu("menuWidget").addClass("overflow");
	$("#submit").button().click(function() {
		postJSON($("#apiTasks")[0].href, {
			submitter : {
				url : theUser
			},
			assay : {
				url : theAssay
			},
			directory : [ {
				name : theDir
			} ]
		}, function(data) {
			addTaskRow($("#tasks"), data);
		});
		return false;
	});
	getJSON($("#apiUsers")[0].href, function(data) {
		dejson(data.user).forEach(function(item) {
			addOption($("#users"), item.id, item.url, item.name);
		});
	});
	getJSON($("#apiAssays")[0].href, function(data) {
		dejson(data.assay).forEach(function(item) {
			addOption($("#assays"), item.id, item.url, item.name);
		});
	});
	getJSON($("#apiDirs")[0].href, function(data) {
		dejson(data.directory).forEach(function(item) {
			var s = item.name.split("/").slice(-2);
			var instrument = s[0];
			var output = s[1];
			addOption($("#dirs"), item.id, item.name, "Instrument: "
					+ instrument + " Dir: " + output);
		});
	});
	getJSON($("#apiTasks")[0].href, function(data) {
		dejson(data.task).forEach(function(t) {
			addTaskRow($("#tasks"), t);
		});
	});
	setInterval(updateProgress, 10000);
});

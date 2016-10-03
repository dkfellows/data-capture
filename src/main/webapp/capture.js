var projectIcon = "images/project.png";
//var investigationIcon = "images/investigation.png";
//var studyIcon = "images/study.png";
//var assayIcon = "images/assay.png";
//var userIcon = "images/user.png";
var instrumentIcon = "images/instrument.png";
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
	opt = $(opt);
	select.append(opt.val(value).text(name));
	return opt;
}
/** How to sort DOM elements. */
function sortChildren(container, property, defaultValue) {
	if (property === undefined)
		property = "sort-key";
	if (defaultValue === undefined)
		defaultValue = "";
	var elems = container.children();
	elems.sort(function(a,b) {
		var aProp = a.getAttribute(property);
		if (aProp === null || aProp === undefined)
			aProp = defaultValue;
		var bProp = b.getAttribute(property);
		if (bProp === null || bProp === undefined)
			bProp = defaultValue;
		return aProp>bProp ? 1 : aProp<bProp ? -1 : 0;
	});
	elems.appendTo(container);
}

/** How to request some JSON asynchronously */
function getJSON(u, done, errors) {
	if (errors === undefined)
		errors = function(jqXHR, textStatus, errorThrown) {
			console.log("problem when fetching " + u, errorThrown);
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
			console.log("problem when posting to " + u, object, errorThrown);
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
/** How to request some JSON asynchronously */
function deleteJSON(u, done, errors) {
	if (errors === undefined)
		errors = function(jqXHR, textStatus, errorThrown) {
			console.log("problem when deleting " + u, errorThrown);
		};
	$.ajax({
		type : "DELETE",
		url : u,
		async : true,
		accept : "application/json",
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
function setProgress(progress, factor, status) {
	var label = progress.find(".progress-label");
	var msg = String(status || "");
	if (msg.length > 0)
		msg += " ";
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
		label.text(val == 100 ? "Done" : msg + val + "%");
		return val == 100;
	}
}
/** Show the spinner. */
function showSpinner() {
	$("#loader").css({
		display : "block"
	});
}
/** Hide the spinner. */
function hideSpinner() {
	$("#loader").css({
		display : "none"
	});
}

/** Create a row of the task table */
function addTaskRow(table, task) {
	/** Create a delete button */
	function delbutn(id) {
		return $("<button id='del_" + id
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
	function cell() {
		var td = $("<td>");
		row.append(td);
		return td;
	}
	/** Create a cell that links to a named thing */
	function linkcell(thing) {
		if(thing !== undefined) {
			var content = $("<a>");
			if (thing.url !== undefined)
				content.attr("href", thing.url);
			var name = thing.name;
			if (name === undefined)
				name = "UNDEFINED";
			return cell().append(content.text(name));
		}
		return cell();
	}
	/** Create a cell containing a timestamp */
	function datecell(timestamp) {
		var c = cell();
		setTimestamp(c, timestamp);
		return c;
	}

	cell().text(task.id);
	linkcell(task.submitter);
	linkcell(task.assay);
	datecell(task["start-time"]).attr("id", "start_" + task.id);
	var progress = progressbar(task.id);
	setProgress(progress, task.progress, task.status);
	cell().append(progress);
	datecell(task["end-time"]).attr("id", "end_" + task.id);
	var asset = task["created-asset"];
	linkcell({
		url : asset === undefined ? "" : asset,
		name : asset === undefined ? "" : "Asset"
	}).attr("id", "asset_" + id);
	cell().append(delbutn(task.id).click(function() {
		var thisRow = $("#" + task.id);
		showSpinner();
		deleteJSON($("#apiTasks")[0].href + "/" + task.id, function(response) {
			hideSpinner();
			console.log("response from delete", response);
			thisRow.remove();
		}, function(jqXHR, textStatus, errorThrown) {
			hideSpinner();
			console.log("response from delete", textStatus);
			thisRow.remove();
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
			setProgress(progress, task.progress, task.status);
			setTimestamp(end, task["end-time"]);
		}, function(a) {
			$("#" + id).remove();
			var asset = task["created-asset"];
			if (asset !== undefined)
				$("#asset_" + id + " a").attr("href", asset).text("Asset");
		});
	}
	var rows = $(".taskrow");
	for (var i=0 ; i<rows.length ; i++) {
		updateRow(rows[i].getAttribute("id"));
	}
	return;
}

function getInstrumentAndDir(item) {
	var s = item.name.split("/").slice(-2);
	var output = s[1];
	s = item.name.split("/");
	var inst = s[2];
	return {
		instrument : inst,
		dir : output
	};
}

function updateDirs() {
	var context = $("#dirs");
	getJSON($("#apiDirs")[0].href, function(data) {
		dejson(data.directory).forEach(function(item) {
			if (document.getElementById(item["@id"]) !== null)
				return;
			var info = getInstrumentAndDir(item);
			addOption(context, item.id, item.name, "Instrument: "
					+ info.instrument + " Dir: " + info.dir).
				attr("sort-key", item.name);
		});
		sortChildren(context);
	});
}

function createIngestTask(user, project, dir, notes) {
	var request = {
		"submitter" : {
			"url" : user
		},
		"directory" : [ {
			"name" : dir
		} ],
		"project" : {
			"url" : project
		},
		"notes" : notes
	};
	console.log("Making ingestion request:", request);
	showSpinner();
	postJSON($("#apiTasks")[0].href, request, function(data) {
		hideSpinner();
		addTaskRow($("#tasks"), data);
	}, function(jqXHR, textStatus, errorThrown) {
		console.log("problem when posting to " + $("#apiTasks")[0].href, request, jqXHR, textStatus, errorThrown);
		hideSpinner();
	});
}

/** What is the currently-selected project? */
var theCurrentProject = undefined;
/** What is the currently-selected directory? */
var theCurrentDir = undefined;
/** What nodes in the directory list are usefully selectable? */
var dirLeaves = {};

function initDirTree() {
	var tree = $("#dirtree");
	var api = $("#apiDirs");
	tree.children().remove();
	try {
		tree.jstree("destroy", true);
	} catch (err) {
		console.log(err);
	}
	tree.empty();
	tree.jstree({
		core : {
			data : {
				url : function(node) {
					return api[0].href;
				},
				data : function(node) {
					return {
						'id' : node.id
					};
				}
			},
			multiple : false
		}
	}).on('select_node.jstree', function(node, selection) {
		theCurrentDir = selection.selected[0];
		updateEnabled();
	});
}

/** Update whether the submit button is enabled. */
function updateEnabled() {
	var theUser = $("#users option:selected").val();
	var theProject = $("#projects option:selected").val();
	var theDir = theCurrentDir;
	var disabled = (theUser == "Choose..." || theProject == "Choose..." || theDir === undefined);
	$("#newOK").button("option", "disabled", disabled);
}

/** Start everything going on page load */
$(function() {
	var theUsers = $("#users"), theDirs = $("#dirs");
	var theProjects = $("#projects"), theNotes = $("#notes");

	theUsers.selectmenu().selectmenu("menuWidget").addClass("overflow");
	theProjects.selectmenu().selectmenu("menuWidget").addClass("overflow");
	var dialog, form;
	function createTask() {
		var theUser = $("#users option:selected").val();
		var theProject = $("#projects option:selected").val();
		var theDir = theCurrentDir;
		var theNotes = $("#notes").val();
		if (theUser === undefined) {
			alert("Please select a Responsible Scientist");
			return false;
		}
		if (theProject === undefined) {
			alert("Please select a SynBioChem Project");
			return false;
		}
		if (theDir === undefined) {
			alert("Please select a Data Directory to upload from");
			return false;
		}
		if (!theDir.match(/.*\/.*/)) {
			alert("Please do not upload an entire machine at once; be more specific")
			return false;
		}
		if (theNotes === undefined) {
			theNotes = "";
		}
		dialog.dialog("close");
		initDirTree();
		createIngestTask(theUser, theProject, theDir, theNotes);
		return true;
	}
	dialog = $("#new").dialog({
		autoOpen: false,
		height: 'auto',
		width: '50%',
		modal: true,
		buttons: [
			{
				id: "newOK",
				text: "Create archiving task",
				click: createTask
			}, {
				text: "Cancel",
				click: function () {
					dialog.dialog("close");
					initDirTree();
				}
			}
		],
		close: function() {
			form[0].reset();
			initDirTree();
		}
	});
	updateEnabled();
	theUsers.on("selectmenuchange", updateEnabled);
	theProjects.on("selectmenuchange", updateEnabled);
	form = dialog.find("form").on("submit", function(event){
		event.preventDefault();
		createTask();
	});
	$("#create-task").button().on("click", function() {
		dialog.dialog("open");
	});
	showSpinner();
	var retrieveCount = 2;
	function doneRetrieve() {
		if (--retrieveCount == 0)
			hideSpinner();
	}
	$("#users_initial").attr("sort-key", "");
	getJSON($("#apiUsers")[0].href, function(data) {
		doneRetrieve();
		dejson(data.user).forEach(function(item) {
			addOption(theUsers, item.id, item.url, item.name).
				attr("sort-key", item.name);
		});
		sortChildren(theUsers);
	});
	getJSON($("#apiProjects")[0].href, function(data) {
		doneRetrieve();
		dejson(data.project).forEach(function(item) {
			addOption(theProjects, item.id, item.url, item.name);
		});
	});
	initDirTree();

	getJSON($("#apiTasks")[0].href, function(data) {
		doneRetrieve();
		var context = $("#tasks");
		dejson(data.task).forEach(function(t) {
			addTaskRow(context, t);
		});
	});
	setInterval(updateProgress, 10000);
	
	/*Scrolling magic, courtesy of StackOverflow */
	$('.treescroll').bind('mousewheel DOMMouseScroll', function(e) {
        var delta = e.wheelDelta || (e.originalEvent && e.originalEvent.wheelDelta) || -e.detail,
        	bottomOverflow = this.scrollTop + $(this).outerHeight() - this.scrollHeight >= 0,
        	topOverflow = this.scrollTop <= 0;
        if ((delta < 0 && bottomOverflow) || (delta > 0 && topOverflow)) {
        	e.preventDefault();
        }
	});
});

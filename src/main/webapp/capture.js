var projectIcon = "images/project.png";
var investigationIcon = "images/investigation.png";
var studyIcon = "images/study.png";
var assayIcon = "images/assay.png";
var userIcon = "images/user.png";
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
function sortChildren(container, property) {
	var elems = container.children();
	elems.sort(function(a,b) {
		var aProp = a.getAttribute(property);
		var bProp = b.getAttribute(property);
		return aProp>bProp ? 1 : aProp<bProp ? -1 : 0;
	});
	elems.detach().appendTo(container);
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

/**
 * Create a row of the task table
 */
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
	function cell(id) {
		var td = $("<td>");
		row.append(td);
		return td;
	}
	/** Create a cell that links to a named thing */
	function linkcell(thing) {
		return cell().append($("<a>").attr("href", thing.url).text(thing.name));
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
		deleteJSON($("#apiTasks")[0].href + "/" + task.id, function(response) {
			console.log("response from delete", response);
			$("#" + task.id).remove();
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
		sortChildren(context, "sort-key");
	});
}
function getAssayTitle(assay) {
	return "Project: '" + assay["project-name"] + "' Study: '"
			+ assay["study-name"] + "' Assay: '" + assay["name"] + "'";
}
function updateAssays() {
	return;
	var context = $("#assays");
	getJSON($("#apiAssays")[0].href, function(data) {
		dejson(data.directory).forEach(function(item) {
			if ($("#" + item.id).length)
				return;
			addOption(context, item.id, item.url, getAssayTitle(item)).
				attr("sort-key", item.name);
		});
		sortChildren(context, "sort-key");
	});
}

/** What is the currently-selected assay? */
var theCurrentAssay = undefined;
/** What is the currently-selected directory? */
var theCurrentDir = undefined;
/** Start everything going on page load */
$(function() {
	var theUsers = $("#users"), theAssays = $("#assays"), theDirs = $("#dirs");

	theUsers.selectmenu().selectmenu("menuWidget").addClass("overflow");
	theAssays.selectmenu().selectmenu("menuWidget").addClass("overflow");
	theDirs.selectmenu().selectmenu("menuWidget").addClass("overflow");
	var dialog, form;
	function createTask() {
		var theUser = $("#users option:selected").val();
		var theAssay = theCurrentAssay;
		var theDir = theCurrentDir;
		if (theUser === undefined || theAssay === undefined || theDir === undefined) {
			alert("please select something in all fields");
			return false;
		}
		var request = {
			submitter : {
				url : theUser
			},
			assay : {
				url : theAssay
			},
			directory : [ {
				name : theDir
			} ]
		};
		dialog.dialog("close");
		postJSON($("#apiTasks")[0].href, request, function(data) {
			addTaskRow($("#tasks"), data);
		});
		return true;
	}
	dialog = $("#new").dialog({
		autoOpen: false,
		height: 'auto',
		width: '50%',
		modal: true,
		buttons: {
			"Create archiving task": createTask,
			"Cancel": function () {
				dialog.dialog("close");
			}
		},
		close: function() {
			form[0].reset();
		}
	});
	form = dialog.find("form").on("submit", function(event){
		event.preventDefault();
		createTask();
	});
	$("#create-task").button().on("click", function() {
		dialog.dialog("open");
	});
	getJSON($("#apiUsers")[0].href, function(data) {
		dejson(data.user).forEach(function(item) {
			addOption(theUsers, item.id, item.url, item.name).
				attr("sort-key", item.name);
		});
		sortChildren(theUsers, "sort-key");
	});
	getJSON($("#apiAssays")[0].href, function(data) {
		var treeData = {};
		dejson(data.assay).forEach(function(item) {
			//addOption(theAssays, item.id, item.url, item.name).
			//	attr("sort-key", item.name);
			var parentNode;
			if (item["project-url"] !== undefined) {
				parentNode = "#";
				treeData[item["project-url"]] = {
					id: item["project-url"],
					parent: parentNode,
					icon: projectIcon,
					state: { opened: true, disabled: true },
					text: "Project: " + item["project-name"]
				};
			}
			if (item["investigation-url"] !== undefined) {
				parentNode = "#";
				if (item["project-url"] !== undefined)
					parentNode = item["project-url"];
				treeData[item["investigation-url"]] = {
					id: item["investigation-url"],
					parent: parentNode,
					icon: investigationIcon,
					state: { opened: true, disabled: true },
					text: "Investigation: " + item["investigation-name"]
				};
			}
			if (item["study-url"] !== undefined) {
				parentNode = "#";
				if (item["investigation-url"] !== undefined)
					parentNode = item["investigation-url"];
				else if (item["project-url"] !== undefined)
					parentNode = item["project-url"];
				treeData[item["study-url"]] = {
					id: item["study-url"],
					parent: parentNode,
					icon: studyIcon,
					text: "Study: " + item["study-name"]
				};
			}
			parentNode = "#";
			if (item["study-url"] !== undefined)
				parentNode = item["study-url"];
			else if (item["investigation-url"] !== undefined)
				parentNode = item["investigation-url"];
			else if (item["project-url"] !== undefined)
				parentNode = item["project-url"];
			treeData[item.url] = {
				id: item.url,
				parent: parentNode,
				icon: assayIcon,
				text: "Assay: " + item.name
			};
		});
		var theData = Object.keys(treeData).map(function(x){return treeData[x];});
		theData.sort(function(a,b){return a.text<b.text?-1:a.text>b.text?1:0;});
		$("#experimenttree").jstree({
			core: {"data": theData, multiple: false}
		}).on('select_node.jstree', function(node, selection){
			var sel = selection.selected[0];
			if (sel.match(/assays/)) {
				theCurrentAssay = sel;
			} else {
				theCurrentAssay = undefined;
			}
		});
		//sortChildren(theAssays, "sort-key");
	});
	getJSON($("#apiDirs")[0].href, function(data) {
		theDirs.children().each(function(){
			var a = $(this).attr("sort-key");
			if (a === undefined) {
				$(this).attr("sort-key", "");
			}
		});
		var treeData = {};
		dejson(data.directory).forEach(function(item) {
			var bits = item.name.split("/");
			var instrument = bits.slice(0,3).join("/");
			treeData[instrument] = {
				id: instrument,
				parent: "#",
				icon: instrumentIcon,
				text: "Instrument: " + bits[2]
			};
			if (bits.length == 5) {
				var person = bits.slice(0,4).join("/");
				treeData[person] = {
					id: person,
					parent: instrument,
					icon: userIcon,
					text: "Experimenter: " + bits[3]
				};
				treeData[item.name] = {
					id: item.name,
					parent: person,
					text: "Data: " + bits.slice(4).join("/")
				};
			} else {
				treeData[item.name] = {
					id: item.name,
					parent: instrument,
					text: "Data: " + bits.slice(3).join("/")
				};
			}
			//var info = getInstrumentAndDir(item);
			//addOption(theDirs, item["@id"], item.name, "Instrument: "
			//		+ info.instrument + " Dir: " + info.dir).
			//	attr("sort-key", item.name);
		});
		treeData = Object.keys(treeData).map(function(x){return treeData[x];});
		treeData.sort(function(a,b){return a.text<b.text?-1:a.text>b.text?1:0;});
		$("#dirtree").jstree({
			core: {"data": treeData, multiple: false}
		}).on('select_node.jstree', function(node, selection){
			var sel = selection.selected[0];
			if (sel.split("/").length > 4) {
				theCurrentDir = sel;
			} else {
				theCurrentDir = undefined;
			}
		});
	});
	getJSON($("#apiTasks")[0].href, function(data) {
		var context = $("#tasks");
		dejson(data.task).forEach(function(t) {
			addTaskRow(context, t);
		});
	});
	setInterval(updateProgress, 10000);
	//setInterval(updateDirs, 30000)
	//setInterval(updateAssays, 30000)
	
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

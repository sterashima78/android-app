extends Control

const BASE_PUZZLE = [
	5,3,0,0,7,0,0,0,0,
	6,0,0,1,9,5,0,0,0,
	0,9,8,0,0,0,0,6,0,
	8,0,0,0,6,0,0,0,3,
	4,0,0,8,0,3,0,0,1,
	7,0,0,0,2,0,0,0,6,
	0,6,0,0,0,0,2,8,0,
	0,0,0,4,1,9,0,0,5,
	0,0,0,0,8,0,0,7,9,
]

const BASE_SOLUTION = [
	5,3,4,6,7,8,9,1,2,
	6,7,2,1,9,5,3,4,8,
	1,9,8,3,4,2,5,6,7,
	8,5,9,7,6,1,4,2,3,
	4,2,6,8,5,3,7,9,1,
	7,1,3,9,2,4,8,5,6,
	9,6,1,5,3,7,2,8,4,
	2,8,7,4,1,9,6,3,5,
	3,4,5,2,8,6,1,7,9,
]

const COLOR_BACKGROUND = Color("10131f")
const COLOR_PANEL = Color("1a2032")
const COLOR_CELL = Color("20283b")
const COLOR_GIVEN = Color("25334d")
const COLOR_SELECTED = Color("6657d9")
const COLOR_RELATED = Color("303b59")
const COLOR_ACCENT = Color("8b7cff")
const COLOR_SUCCESS = Color("62d6a5")
const COLOR_ERROR = Color("ff738a")
const COLOR_TEXT = Color("f5f6fb")
const COLOR_MUTED = Color("aeb7cc")
const COLOR_GRID = Color("58647d")

var puzzle = []
var solution = []
var values = []
var cells = []
var selected_index := -1
var mistakes := 0
var elapsed_seconds := 0.0
var completed := false
var rng := RandomNumberGenerator.new()

var board_panel: PanelContainer
var grid: GridContainer
var timer_label: Label
var mistake_label: Label
var status_label: Label
var completion_layer: Control
var completion_card: PanelContainer

func _ready():
	rng.randomize()
	_build_ui()
	_new_game(false)
	call_deferred("_play_intro")

func _process(delta):
	if not completed:
		elapsed_seconds += delta
	_update_timer()

func _build_ui():
	var background = ColorRect.new()
	background.color = COLOR_BACKGROUND
	background.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(background)

	_add_background_glow(background, Vector2(860, 180), Vector2(360, 360), Color(0.28, 0.22, 0.72, 0.18), 18.0)
	_add_background_glow(background, Vector2(80, 1450), Vector2(420, 420), Color(0.10, 0.55, 0.62, 0.12), -16.0)

	var safe = MarginContainer.new()
	safe.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	safe.add_theme_constant_override("margin_left", 48)
	safe.add_theme_constant_override("margin_right", 48)
	safe.add_theme_constant_override("margin_top", 52)
	safe.add_theme_constant_override("margin_bottom", 46)
	add_child(safe)

	var content = VBoxContainer.new()
	content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	content.size_flags_vertical = Control.SIZE_EXPAND_FILL
	content.add_theme_constant_override("separation", 22)
	safe.add_child(content)

	content.add_child(_build_header())
	content.add_child(_build_stats())

	var board_center = CenterContainer.new()
	board_center.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	board_center.size_flags_vertical = Control.SIZE_EXPAND_FILL
	content.add_child(board_center)
	board_panel = _build_board()
	board_center.add_child(board_panel)

	status_label = Label.new()
	status_label.text = "空いているマスを選んで数字を入力"
	status_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	status_label.add_theme_font_size_override("font_size", 28)
	status_label.add_theme_color_override("font_color", COLOR_MUTED)
	content.add_child(status_label)

	content.add_child(_build_number_pad())

	var footer = Label.new()
	footer.text = "Godot Control + Tween で描画・入力・アニメーションを実装した POC"
	footer.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	footer.add_theme_font_size_override("font_size", 21)
	footer.add_theme_color_override("font_color", Color(COLOR_MUTED, 0.72))
	content.add_child(footer)

	_build_completion_layer()

func _build_header():
	var row = HBoxContainer.new()
	row.add_theme_constant_override("separation", 20)

	var back = Button.new()
	back.text = "‹ ゲーム"
	back.custom_minimum_size = Vector2(170, 76)
	back.focus_mode = Control.FOCUS_NONE
	back.add_theme_font_size_override("font_size", 27)
	back.add_theme_stylebox_override("normal", _rounded_style(Color(0.12, 0.15, 0.23, 0.9), 22))
	back.add_theme_stylebox_override("pressed", _rounded_style(Color(0.18, 0.20, 0.31, 1.0), 22))
	back.pressed.connect(_on_back_pressed)
	row.add_child(back)

	var titles = VBoxContainer.new()
	titles.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	titles.add_theme_constant_override("separation", 0)
	var title = Label.new()
	title.text = "数独"
	title.add_theme_font_size_override("font_size", 48)
	title.add_theme_color_override("font_color", COLOR_TEXT)
	titles.add_child(title)
	var subtitle = Label.new()
	subtitle.text = "GODOT ENGINE POC"
	subtitle.add_theme_font_size_override("font_size", 20)
	subtitle.add_theme_color_override("font_color", COLOR_ACCENT)
	titles.add_child(subtitle)
	row.add_child(titles)

	var reset = Button.new()
	reset.text = "新しい盤面"
	reset.custom_minimum_size = Vector2(190, 76)
	reset.focus_mode = Control.FOCUS_NONE
	reset.add_theme_font_size_override("font_size", 25)
	reset.add_theme_stylebox_override("normal", _rounded_style(Color(0.22, 0.20, 0.40, 0.95), 22))
	reset.add_theme_stylebox_override("pressed", _rounded_style(Color(0.34, 0.29, 0.60, 1.0), 22))
	reset.pressed.connect(_on_new_game_pressed)
	row.add_child(reset)
	return row

func _build_stats():
	var row = HBoxContainer.new()
	row.alignment = BoxContainer.ALIGNMENT_CENTER
	row.add_theme_constant_override("separation", 18)

	var timer_chip = _stat_chip("TIME")
	timer_label = timer_chip.get_node("Value")
	timer_label.text = "00:00"
	row.add_child(timer_chip)

	var mistake_chip = _stat_chip("MISS")
	mistake_label = mistake_chip.get_node("Value")
	mistake_label.text = "0"
	row.add_child(mistake_chip)
	return row

func _stat_chip(caption: String):
	var panel = PanelContainer.new()
	panel.custom_minimum_size = Vector2(210, 78)
	panel.add_theme_stylebox_override("panel", _rounded_style(Color(0.10, 0.13, 0.20, 0.92), 24))
	var row = HBoxContainer.new()
	row.alignment = BoxContainer.ALIGNMENT_CENTER
	row.add_theme_constant_override("separation", 14)
	panel.add_child(row)
	var label = Label.new()
	label.text = caption
	label.add_theme_font_size_override("font_size", 20)
	label.add_theme_color_override("font_color", COLOR_MUTED)
	row.add_child(label)
	var value = Label.new()
	value.name = "Value"
	value.add_theme_font_size_override("font_size", 31)
	value.add_theme_color_override("font_color", COLOR_TEXT)
	row.add_child(value)
	return panel

func _build_board():
	var panel = PanelContainer.new()
	panel.custom_minimum_size = Vector2(920, 920)
	var panel_style = _rounded_style(Color(0.065, 0.078, 0.12, 0.98), 34)
	panel_style.content_margin_left = 16
	panel_style.content_margin_right = 16
	panel_style.content_margin_top = 16
	panel_style.content_margin_bottom = 16
	panel.add_theme_stylebox_override("panel", panel_style)

	grid = GridContainer.new()
	grid.columns = 9
	grid.add_theme_constant_override("h_separation", 3)
	grid.add_theme_constant_override("v_separation", 3)
	panel.add_child(grid)

	for i in range(81):
		var button = Button.new()
		button.custom_minimum_size = Vector2(95, 95)
		button.focus_mode = Control.FOCUS_NONE
		button.add_theme_font_size_override("font_size", 39)
		button.pressed.connect(_on_cell_pressed.bind(i))
		grid.add_child(button)
		cells.append(button)
	return panel

func _build_number_pad():
	var center = CenterContainer.new()
	center.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var row = HBoxContainer.new()
	row.alignment = BoxContainer.ALIGNMENT_CENTER
	row.add_theme_constant_override("separation", 9)
	center.add_child(row)
	for number in range(1, 10):
		var button = Button.new()
		button.text = str(number)
		button.custom_minimum_size = Vector2(91, 86)
		button.focus_mode = Control.FOCUS_NONE
		button.add_theme_font_size_override("font_size", 36)
		button.add_theme_color_override("font_color", COLOR_TEXT)
		button.add_theme_stylebox_override("normal", _rounded_style(Color(0.12, 0.15, 0.23, 0.96), 22))
		button.add_theme_stylebox_override("hover", _rounded_style(Color(0.18, 0.21, 0.33, 1.0), 22))
		button.add_theme_stylebox_override("pressed", _rounded_style(COLOR_SELECTED, 22))
		button.pressed.connect(_on_number_pressed.bind(number))
		row.add_child(button)
	return center

func _build_completion_layer():
	completion_layer = Control.new()
	completion_layer.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	completion_layer.mouse_filter = Control.MOUSE_FILTER_STOP
	completion_layer.visible = false
	add_child(completion_layer)

	var shade = ColorRect.new()
	shade.color = Color(0.02, 0.025, 0.045, 0.80)
	shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	completion_layer.add_child(shade)

	var center = CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	completion_layer.add_child(center)

	completion_card = PanelContainer.new()
	completion_card.custom_minimum_size = Vector2(690, 430)
	var style = _rounded_style(Color(0.10, 0.13, 0.21, 0.99), 42)
	style.border_color = Color(COLOR_ACCENT, 0.75)
	style.border_width_left = 3
	style.border_width_right = 3
	style.border_width_top = 3
	style.border_width_bottom = 3
	style.content_margin_left = 56
	style.content_margin_right = 56
	style.content_margin_top = 48
	style.content_margin_bottom = 48
	completion_card.add_theme_stylebox_override("panel", style)
	center.add_child(completion_card)

	var box = VBoxContainer.new()
	box.alignment = BoxContainer.ALIGNMENT_CENTER
	box.add_theme_constant_override("separation", 22)
	completion_card.add_child(box)

	var mark = Label.new()
	mark.text = "✓"
	mark.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	mark.add_theme_font_size_override("font_size", 88)
	mark.add_theme_color_override("font_color", COLOR_SUCCESS)
	box.add_child(mark)
	var title = Label.new()
	title.text = "完成！"
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_font_size_override("font_size", 52)
	title.add_theme_color_override("font_color", COLOR_TEXT)
	box.add_child(title)
	var message = Label.new()
	message.name = "Message"
	message.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	message.add_theme_font_size_override("font_size", 27)
	message.add_theme_color_override("font_color", COLOR_MUTED)
	box.add_child(message)
	var again = Button.new()
	again.text = "もう一度"
	again.custom_minimum_size = Vector2(360, 80)
	again.focus_mode = Control.FOCUS_NONE
	again.add_theme_font_size_override("font_size", 29)
	again.add_theme_stylebox_override("normal", _rounded_style(COLOR_SELECTED, 24))
	again.add_theme_stylebox_override("pressed", _rounded_style(Color("7669ef"), 24))
	again.pressed.connect(_on_new_game_pressed)
	box.add_child(again)

func _add_background_glow(parent: Control, position_value: Vector2, size_value: Vector2, color_value: Color, rotation_degrees: float):
	var glow = ColorRect.new()
	glow.position = position_value
	glow.size = size_value
	glow.color = color_value
	glow.rotation = deg_to_rad(rotation_degrees)
	glow.mouse_filter = Control.MOUSE_FILTER_IGNORE
	parent.add_child(glow)

func _new_game(animated := true):
	var digits = [1,2,3,4,5,6,7,8,9]
	digits.shuffle()
	puzzle = []
	solution = []
	for value in BASE_PUZZLE:
		puzzle.append(0 if value == 0 else digits[value - 1])
	for value in BASE_SOLUTION:
		solution.append(digits[value - 1])
	values = puzzle.duplicate()
	selected_index = -1
	mistakes = 0
	elapsed_seconds = 0.0
	completed = false
	mistake_label.text = "0"
	status_label.text = "空いているマスを選んで数字を入力"
	status_label.add_theme_color_override("font_color", COLOR_MUTED)
	completion_layer.visible = false
	_refresh_board()
	if animated:
		_animate_new_board()

func _refresh_board():
	for i in range(81):
		var button = cells[i]
		var value = values[i]
		var is_given = puzzle[i] != 0
		var is_selected = i == selected_index
		var is_related = false
		if selected_index >= 0:
			var sr = selected_index / 9
			var sc = selected_index % 9
			var row = i / 9
			var col = i % 9
			is_related = row == sr or col == sc or (row / 3 == sr / 3 and col / 3 == sc / 3)

		button.text = "" if value == 0 else str(value)
		button.disabled = is_given
		button.add_theme_color_override("font_color", COLOR_TEXT if is_given else COLOR_ACCENT)
		button.add_theme_color_override("font_disabled_color", COLOR_TEXT)
		button.add_theme_color_override("font_pressed_color", COLOR_TEXT)
		button.add_theme_stylebox_override("normal", _cell_style(i, is_given, is_selected, is_related))
		button.add_theme_stylebox_override("disabled", _cell_style(i, is_given, is_selected, is_related))
		button.add_theme_stylebox_override("hover", _cell_style(i, is_given, is_selected, true))
		button.add_theme_stylebox_override("pressed", _cell_style(i, is_given, true, true))
		button.add_theme_stylebox_override("focus", StyleBoxEmpty.new())

func _cell_style(index: int, is_given: bool, is_selected: bool, is_related: bool):
	var color = COLOR_GIVEN if is_given else COLOR_CELL
	if is_related:
		color = COLOR_RELATED
	if is_selected:
		color = COLOR_SELECTED
	var style = StyleBoxFlat.new()
	style.bg_color = color
	style.corner_radius_top_left = 12
	style.corner_radius_top_right = 12
	style.corner_radius_bottom_left = 12
	style.corner_radius_bottom_right = 12
	style.border_color = COLOR_GRID
	var row = index / 9
	var col = index % 9
	style.border_width_left = 3 if col % 3 == 0 else 1
	style.border_width_right = 3 if col % 3 == 2 else 1
	style.border_width_top = 3 if row % 3 == 0 else 1
	style.border_width_bottom = 3 if row % 3 == 2 else 1
	return style

func _on_cell_pressed(index: int):
	if puzzle[index] != 0 or completed:
		return
	selected_index = index
	_refresh_board()
	var cell = cells[index]
	cell.pivot_offset = cell.size * 0.5
	var tween = create_tween()
	tween.set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
	tween.tween_property(cell, "scale", Vector2(1.06, 1.06), 0.08)
	tween.tween_property(cell, "scale", Vector2.ONE, 0.12)

func _on_number_pressed(number: int):
	if selected_index < 0 or completed:
		return
	if puzzle[selected_index] != 0:
		return
	var current = selected_index
	if solution[current] == number:
		values[current] = number
		selected_index = _find_next_empty(current)
		_refresh_board()
		_animate_correct(cells[current])
		status_label.text = "正解。次のマスへ"
		status_label.add_theme_color_override("font_color", COLOR_SUCCESS)
		if _is_complete():
			completed = true
			selected_index = -1
			_refresh_board()
			_show_completion()
	else:
		mistakes += 1
		mistake_label.text = str(mistakes)
		status_label.text = "その数字ではありません"
		status_label.add_theme_color_override("font_color", COLOR_ERROR)
		_animate_error(cells[current])

func _find_next_empty(from_index: int):
	for step in range(1, 82):
		var index = (from_index + step) % 81
		if values[index] == 0:
			return index
	return -1

func _is_complete():
	for i in range(81):
		if values[i] != solution[i]:
			return false
	return true

func _animate_correct(cell: Control):
	cell.pivot_offset = cell.size * 0.5
	cell.scale = Vector2(0.72, 0.72)
	cell.modulate = Color(0.62, 1.0, 0.82, 1.0)
	var tween = create_tween().set_parallel(true)
	tween.set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	tween.tween_property(cell, "scale", Vector2.ONE, 0.34)
	tween.tween_property(cell, "modulate", Color.WHITE, 0.30)

func _animate_error(cell: Control):
	cell.pivot_offset = cell.size * 0.5
	var tween = create_tween()
	tween.set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	tween.tween_property(cell, "rotation", deg_to_rad(4.0), 0.045)
	tween.tween_property(cell, "rotation", deg_to_rad(-4.0), 0.07)
	tween.tween_property(cell, "rotation", deg_to_rad(3.0), 0.06)
	tween.tween_property(cell, "rotation", 0.0, 0.06)

func _animate_new_board():
	board_panel.pivot_offset = board_panel.size * 0.5
	board_panel.scale = Vector2(0.94, 0.94)
	board_panel.modulate.a = 0.35
	var tween = create_tween().set_parallel(true)
	tween.set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tween.tween_property(board_panel, "scale", Vector2.ONE, 0.36)
	tween.tween_property(board_panel, "modulate:a", 1.0, 0.28)

func _play_intro():
	await get_tree().process_frame
	board_panel.pivot_offset = board_panel.size * 0.5
	board_panel.scale = Vector2(0.86, 0.86)
	board_panel.modulate.a = 0.0
	var tween = create_tween().set_parallel(true)
	tween.set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	tween.tween_property(board_panel, "scale", Vector2.ONE, 0.62).set_delay(0.06)
	tween.tween_property(board_panel, "modulate:a", 1.0, 0.38).set_delay(0.04)

func _show_completion():
	completion_layer.visible = true
	var message = completion_card.get_node("VBoxContainer/Message") if completion_card.has_node("VBoxContainer/Message") else null
	if message:
		message.text = "TIME %s   ·   MISS %d" % [_formatted_time(), mistakes]
	completion_card.pivot_offset = completion_card.size * 0.5
	completion_card.scale = Vector2(0.72, 0.72)
	completion_card.modulate.a = 0.0
	var tween = create_tween().set_parallel(true)
	tween.set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	tween.tween_property(completion_card, "scale", Vector2.ONE, 0.55)
	tween.tween_property(completion_card, "modulate:a", 1.0, 0.30)
	_spawn_confetti()

func _spawn_confetti():
	var palette = [COLOR_ACCENT, COLOR_SUCCESS, Color("ffd166"), COLOR_ERROR, Color("62b8ff")]
	for i in range(34):
		var piece = ColorRect.new()
		piece.mouse_filter = Control.MOUSE_FILTER_IGNORE
		piece.color = palette[i % palette.size()]
		piece.size = Vector2(rng.randi_range(12, 26), rng.randi_range(18, 34))
		piece.position = Vector2(rng.randf_range(40.0, 1040.0), rng.randf_range(-120.0, 80.0))
		piece.rotation = rng.randf_range(-1.0, 1.0)
		completion_layer.add_child(piece)
		var destination = piece.position + Vector2(rng.randf_range(-180.0, 180.0), rng.randf_range(1400.0, 2050.0))
		var duration = rng.randf_range(1.6, 2.8)
		var drop = create_tween().set_parallel(true)
		drop.set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
		drop.tween_property(piece, "position", destination, duration).set_delay(rng.randf_range(0.0, 0.35))
		drop.tween_property(piece, "rotation", piece.rotation + rng.randf_range(3.0, 8.0), duration)
		drop.tween_property(piece, "modulate:a", 0.0, 0.45).set_delay(duration - 0.35)
		drop.finished.connect(piece.queue_free)

func _on_new_game_pressed():
	_new_game(true)

func _on_back_pressed():
	get_tree().quit()

func _update_timer():
	if timer_label:
		timer_label.text = _formatted_time()

func _formatted_time():
	var total = int(elapsed_seconds)
	return "%02d:%02d" % [total / 60, total % 60]

func _rounded_style(color: Color, radius: int):
	var style = StyleBoxFlat.new()
	style.bg_color = color
	style.corner_radius_top_left = radius
	style.corner_radius_top_right = radius
	style.corner_radius_bottom_left = radius
	style.corner_radius_bottom_right = radius
	style.content_margin_left = 16
	style.content_margin_right = 16
	style.content_margin_top = 10
	style.content_margin_bottom = 10
	return style

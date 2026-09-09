extends Control

const COLOR_TABLE = Color("123f35")
const COLOR_TABLE_DARK = Color("0b2c25")
const COLOR_CARD = Color("f7f3ea")
const COLOR_CARD_TEXT = Color("15171b")
const COLOR_RED = Color("c63d4f")
const COLOR_BACK = Color("274f7d")
const COLOR_BACK_ACCENT = Color("6d9ed4")
const COLOR_SLOT = Color(1, 1, 1, 0.10)
const COLOR_SELECTED = Color("f7c948")
const COLOR_TARGET = Color("63d7c5")
const COLOR_MUTED = Color(1, 1, 1, 0.72)

var game := KlondikeModel.new()
var surface: Control

func _ready():
	get_window().content_scale_size = Vector2i(1920, 1080)
	DisplayServer.screen_set_orientation(DisplayServer.SCREEN_SENSOR_LANDSCAPE)
	set_process_unhandled_key_input(true)
	surface = Control.new()
	surface.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(surface)
	resized.connect(_render)
	call_deferred("_render")

func _notification(what):
	if what == NOTIFICATION_WM_GO_BACK_REQUEST:
		get_tree().quit()

func _unhandled_key_input(event):
	if event.is_action_pressed("ui_cancel"):
		get_tree().quit()
		get_viewport().set_input_as_handled()

func _render():
	if surface == null:
		return
	for child in surface.get_children():
		child.free()

	var viewport_size := size
	if viewport_size.x < 100 or viewport_size.y < 100:
		return

	var background = ColorRect.new()
	background.position = Vector2.ZERO
	background.size = viewport_size
	background.color = COLOR_TABLE
	background.mouse_filter = Control.MOUSE_FILTER_IGNORE
	surface.add_child(background)

	var shade = ColorRect.new()
	shade.position = Vector2(0, viewport_size.y * 0.72)
	shade.size = Vector2(viewport_size.x, viewport_size.y * 0.28)
	shade.color = Color(COLOR_TABLE_DARK.r, COLOR_TABLE_DARK.g, COLOR_TABLE_DARK.b, 0.35)
	shade.mouse_filter = Control.MOUSE_FILTER_IGNORE
	surface.add_child(shade)

	_build_top_row(viewport_size)
	_build_tableau(viewport_size)
	if game.is_won():
		_build_win_overlay(viewport_size)

func _build_top_row(viewport_size: Vector2):
	var y := 20.0
	var card_w := 104.0
	var card_h := 144.0
	var gap := 12.0

	_add_action_button("‹ ゲーム", Rect2(24, 24, 116, 56), func(): get_tree().quit())

	var stock_rect := Rect2(164, y, card_w, card_h)
	if game.stock.is_empty():
		var stock_label := "↻" if not game.waste.is_empty() else "山札"
		_add_slot_button(stock_label, stock_rect, not game.waste.is_empty(), func():
			game.draw_stock()
			_render()
		)
	else:
		_add_back_button("山札 %d" % game.stock.size(), stock_rect, func():
			game.draw_stock()
			_render()
		)

	var waste_rect := Rect2(stock_rect.end.x + gap, y, card_w, card_h)
	if game.waste.is_empty():
		_add_slot_button("捨て札", waste_rect, false, Callable())
	else:
		var waste_selected := _selection_is("waste")
		_add_card_button(game.waste.back(), waste_rect, waste_selected, false, func():
			game.select_waste()
			_render()
		)

	var new_rect := Rect2(viewport_size.x - 144, 24, 120, 56)
	_add_action_button("新しいゲーム", new_rect, func():
		game.reset()
		_render()
	)

	var foundation_start := new_rect.position.x - (card_w + gap) * 4
	var valid_foundations := game.valid_foundation_targets()
	for suit in KlondikeModel.SUITS:
		var foundation_rect := Rect2(foundation_start + suit * (card_w + gap), y, card_w, card_h)
		var foundation: Array = game.foundations[suit]
		var is_target: bool = suit in valid_foundations
		if foundation.is_empty():
			_add_slot_button("A %s" % KlondikeModel.suit_symbol(suit), foundation_rect, is_target, func(target_suit = suit):
				game.move_selected_to_foundation(target_suit)
				_render()
			)
		else:
			var is_selected := _selection_is_foundation(suit)
			_add_card_button(foundation.back(), foundation_rect, is_selected, is_target, func(target_suit = suit):
				if target_suit in game.valid_foundation_targets():
					game.move_selected_to_foundation(target_suit)
				else:
					game.select_foundation(target_suit)
				_render()
			)

	var title_left := waste_rect.end.x + 28
	var title_right := foundation_start - 28
	var title_width := max(160.0, title_right - title_left)
	_add_label("クロンダイク", Rect2(title_left, 34, title_width, 44), 34, Color.WHITE, HORIZONTAL_ALIGNMENT_CENTER)
	_add_label("%d 手" % game.moves, Rect2(title_left, 80, title_width, 34), 24, COLOR_MUTED, HORIZONTAL_ALIGNMENT_CENTER)

	if game.selection != null:
		var message := "%d 枚を選択" % game.selected_card_count()
		if not game.valid_foundation_targets().is_empty():
			message += " · 組札または強調列へ"
		elif not game.valid_tableau_targets().is_empty():
			message += " · 強調列へ"
		else:
			message += " · 移動先なし"
		_add_label(message, Rect2(title_left, 116, title_width, 30), 19, COLOR_MUTED, HORIZONTAL_ALIGNMENT_CENTER)

func _add_label(text_value: String, rect: Rect2, font_size: int, color: Color, alignment: int):
	var label = Label.new()
	label.text = text_value
	label.position = rect.position
	label.size = rect.size
	label.horizontal_alignment = alignment
	label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	label.add_theme_font_size_override("font_size", font_size)
	label.add_theme_color_override("font_color", color)
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	surface.add_child(label)

func _add_action_button(text_value: String, rect: Rect2, callback: Callable):
	var button := _base_button(text_value, rect, callback)
	button.add_theme_font_size_override("font_size", 18)
	button.add_theme_color_override("font_color", Color.WHITE)
	button.add_theme_stylebox_override("normal", _rounded_style(Color(0, 0, 0, 0.28), Color(1, 1, 1, 0.14), 1, 18))
	button.add_theme_stylebox_override("pressed", _rounded_style(Color(0, 0, 0, 0.48), Color(1, 1, 1, 0.24), 1, 18))

func _base_button(text_value: String, rect: Rect2, callback: Callable) -> Button:
	var button = Button.new()
	button.text = text_value
	button.position = rect.position
	button.size = rect.size
	button.focus_mode = Control.FOCUS_NONE
	button.clip_text = true
	if callback.is_valid():
		button.pressed.connect(callback)
	surface.add_child(button)
	return button

func _rounded_style(background: Color, border: Color, border_width: int, radius: int) -> StyleBoxFlat:
	var style = StyleBoxFlat.new()
	style.bg_color = background
	style.border_color = border
	style.border_width_left = border_width
	style.border_width_top = border_width
	style.border_width_right = border_width
	style.border_width_bottom = border_width
	style.corner_radius_top_left = radius
	style.corner_radius_top_right = radius
	style.corner_radius_bottom_left = radius
	style.corner_radius_bottom_right = radius
	return style

extends Control

const KlondikeModel = preload("res://klondike_model.gd")

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

var game = KlondikeModel.new()
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

	var viewport_size: Vector2 = size
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
		var waste_selected: bool = _selection_is("waste")
		_add_card_button(game.waste.back(), waste_rect, waste_selected, false, func():
			game.select_waste()
			_render()
		)

	var new_rect := Rect2(viewport_size.x - 144, 24, 120, 56)
	_add_action_button("新しいゲーム", new_rect, func():
		game.reset()
		_render()
	)

	var foundation_start: float = new_rect.position.x - (card_w + gap) * 4
	var valid_foundations: Array = game.valid_foundation_targets()
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
			var is_selected: bool = _selection_is_foundation(suit)
			_add_card_button(foundation.back(), foundation_rect, is_selected, is_target, func(target_suit = suit):
				if target_suit in game.valid_foundation_targets():
					game.move_selected_to_foundation(target_suit)
				else:
					game.select_foundation(target_suit)
				_render()
			)

	var title_left: float = waste_rect.end.x + 28
	var title_right: float = foundation_start - 28
	var title_width: float = max(160.0, title_right - title_left)
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

func _build_tableau(viewport_size: Vector2):
	var top := 184.0
	var bottom: float = viewport_size.y - 18.0
	var board_width: float = min(viewport_size.x - 80.0, 1500.0)
	var gap := 14.0
	var card_w: float = (board_width - gap * 6.0) / 7.0
	var card_h: float = min(card_w * 1.38, 274.0)
	var left: float = (viewport_size.x - board_width) * 0.5
	var available_height: float = bottom - top
	var valid_targets: Array = game.valid_tableau_targets()

	for pile_index in range(7):
		var pile: Array = game.tableau[pile_index]
		var x: float = left + pile_index * (card_w + gap)
		if pile.is_empty():
			var is_target: bool = pile_index in valid_targets
			_add_slot_button("K", Rect2(x, top, card_w, card_h), is_target, func(target_pile = pile_index):
				game.move_selected_to_tableau(target_pile)
				_render()
			)
			continue

		var step := 0.0
		if pile.size() > 1:
			step = clamp((available_height - card_h) / float(pile.size() - 1), 22.0, 54.0)

		for card_index in range(pile.size()):
			var tableau_card: Dictionary = pile[card_index]
			var rect := Rect2(x, top + step * card_index, card_w, card_h)
			var is_top: bool = card_index == pile.size() - 1
			if tableau_card["face_up"]:
				var is_selected: bool = _selection_contains_tableau_card(pile_index, card_index)
				var is_target: bool = is_top and pile_index in valid_targets
				_add_card_button(tableau_card["card"], rect, is_selected, is_target, func(target_pile = pile_index, target_card = card_index):
					if target_pile in game.valid_tableau_targets():
						game.move_selected_to_tableau(target_pile)
					else:
						game.select_tableau(target_pile, target_card)
					_render()
				)
			else:
				var callback := Callable()
				if is_top and game.selection == null:
					callback = func(target_pile = pile_index):
						game.flip_tableau_top(target_pile)
						_render()
				_add_back_button("◆", rect, callback)

func _add_card_button(card: Dictionary, rect: Rect2, selected: bool, target: bool, callback: Callable):
	var label := "%s %s" % [KlondikeModel.rank_label(card["rank"]), KlondikeModel.suit_symbol(card["suit"])]
	var button := _base_button(label, rect, callback)
	var foreground: Color = COLOR_RED if KlondikeModel.suit_is_red(card["suit"]) else COLOR_CARD_TEXT
	var border: Color = COLOR_SELECTED if selected else (COLOR_TARGET if target else Color(0, 0, 0, 0.22))
	var width: int = 5 if selected or target else 1
	button.add_theme_font_size_override("font_size", int(clamp(rect.size.x * 0.19, 23.0, 36.0)))
	button.add_theme_color_override("font_color", foreground)
	button.add_theme_color_override("font_hover_color", foreground)
	button.add_theme_color_override("font_pressed_color", foreground)
	button.add_theme_stylebox_override("normal", _rounded_style(COLOR_CARD, border, width, 12))
	button.add_theme_stylebox_override("hover", _rounded_style(Color("fffdf8"), border, width, 12))
	button.add_theme_stylebox_override("pressed", _rounded_style(Color("e9e2d4"), border, width, 12))

func _add_back_button(text_value: String, rect: Rect2, callback: Callable):
	var button := _base_button(text_value, rect, callback)
	button.add_theme_font_size_override("font_size", int(clamp(rect.size.x * 0.17, 20.0, 32.0)))
	button.add_theme_color_override("font_color", Color(1, 1, 1, 0.88))
	button.add_theme_stylebox_override("normal", _rounded_style(COLOR_BACK, COLOR_BACK_ACCENT, 3, 12))
	button.add_theme_stylebox_override("hover", _rounded_style(Color("315f91"), COLOR_BACK_ACCENT, 3, 12))
	button.add_theme_stylebox_override("pressed", _rounded_style(Color("1d416b"), COLOR_BACK_ACCENT, 3, 12))

func _add_slot_button(text_value: String, rect: Rect2, active: bool, callback: Callable):
	var button := _base_button(text_value, rect, callback if active else Callable())
	var border: Color = COLOR_TARGET if active else Color(1, 1, 1, 0.28)
	var width: int = 4 if active else 2
	button.add_theme_font_size_override("font_size", int(clamp(rect.size.x * 0.16, 18.0, 30.0)))
	button.add_theme_color_override("font_color", Color(1, 1, 1, 0.58))
	button.add_theme_stylebox_override("normal", _rounded_style(COLOR_SLOT, border, width, 12))
	button.add_theme_stylebox_override("hover", _rounded_style(Color(1, 1, 1, 0.15), border, width, 12))
	button.add_theme_stylebox_override("pressed", _rounded_style(Color(1, 1, 1, 0.20), border, width, 12))

func _build_win_overlay(viewport_size: Vector2):
	var shade = ColorRect.new()
	shade.position = Vector2.ZERO
	shade.size = viewport_size
	shade.color = Color(0, 0, 0, 0.70)
	shade.mouse_filter = Control.MOUSE_FILTER_STOP
	surface.add_child(shade)

	var card_rect := Rect2((viewport_size.x - 620.0) * 0.5, (viewport_size.y - 330.0) * 0.5, 620, 330)
	var panel = PanelContainer.new()
	panel.position = card_rect.position
	panel.size = card_rect.size
	panel.add_theme_stylebox_override("panel", _rounded_style(Color("17372f"), COLOR_TARGET, 3, 28))
	surface.add_child(panel)

	_add_label("クリア", Rect2(card_rect.position.x + 40, card_rect.position.y + 42, 540, 70), 52, Color.WHITE, HORIZONTAL_ALIGNMENT_CENTER)
	_add_label("52枚すべてを組札へ移動しました", Rect2(card_rect.position.x + 40, card_rect.position.y + 120, 540, 50), 25, COLOR_MUTED, HORIZONTAL_ALIGNMENT_CENTER)
	_add_label("%d 手" % game.moves, Rect2(card_rect.position.x + 40, card_rect.position.y + 166, 540, 40), 22, COLOR_MUTED, HORIZONTAL_ALIGNMENT_CENTER)
	_add_action_button("次のゲーム", Rect2(card_rect.position.x + 180, card_rect.position.y + 230, 260, 64), func():
		game.reset()
		_render()
	)

func _selection_is(kind: String) -> bool:
	return game.selection != null and game.selection["kind"] == kind

func _selection_is_foundation(suit: int) -> bool:
	return _selection_is("foundation") and game.selection["suit"] == suit

func _selection_contains_tableau_card(pile_index: int, card_index: int) -> bool:
	return _selection_is("tableau") and game.selection["pile"] == pile_index and card_index >= game.selection["card"]

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
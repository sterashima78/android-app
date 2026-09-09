class_name KlondikeModel
extends RefCounted

enum Suit {
	CLUBS,
	DIAMONDS,
	HEARTS,
	SPADES,
}

const SUITS = [Suit.CLUBS, Suit.DIAMONDS, Suit.HEARTS, Suit.SPADES]

var stock: Array = []
var waste: Array = []
var foundations: Array = []
var tableau: Array = []
var selection = null
var moves := 0
var rng := RandomNumberGenerator.new()

func _init():
	reset()

func reset(seed_value = null):
	if seed_value == null:
		rng.randomize()
	else:
		rng.seed = int(seed_value)

	var deck: Array = []
	for suit in SUITS:
		for rank in range(1, 14):
			deck.append({"suit": suit, "rank": rank})
	_shuffle(deck)

	tableau = []
	var cursor := 0
	for pile_index in range(7):
		var pile: Array = []
		for card_index in range(pile_index + 1):
			pile.append({
				"card": deck[cursor],
				"face_up": card_index == pile_index,
			})
			cursor += 1
		tableau.append(pile)

	stock = deck.slice(cursor)
	waste = []
	foundations = [[], [], [], []]
	selection = null
	moves = 0

func draw_stock():
	if not stock.is_empty():
		waste.append(stock.pop_back())
		selection = null
		moves += 1
	elif not waste.is_empty():
		stock = waste.duplicate()
		stock.reverse()
		waste = []
		selection = null
		moves += 1

func select_waste():
	if waste.is_empty() or is_won():
		return
	_toggle_selection({"kind": "waste"})

func select_foundation(suit: int):
	if foundations[suit].is_empty() or is_won():
		return
	_toggle_selection({"kind": "foundation", "suit": suit})

func select_tableau(pile_index: int, card_index: int):
	if is_won() or pile_index < 0 or pile_index >= tableau.size():
		return
	var pile: Array = tableau[pile_index]
	if card_index < 0 or card_index >= pile.size() or not pile[card_index]["face_up"]:
		return
	_toggle_selection({"kind": "tableau", "pile": pile_index, "card": card_index})

func selected_card_count() -> int:
	var cards = _selected_cards()
	return 0 if cards == null else cards.size()

func valid_tableau_targets() -> Array:
	if selection == null:
		return []
	var moving = _selected_cards()
	if moving == null or not _is_valid_tableau_run(moving):
		return []

	var targets: Array = []
	for pile_index in range(tableau.size()):
		if selection["kind"] == "tableau" and selection["pile"] == pile_index:
			continue
		var target = null if tableau[pile_index].is_empty() else tableau[pile_index].back()
		if _can_place_on_tableau(moving[0], target):
			targets.append(pile_index)
	return targets

func valid_foundation_targets() -> Array:
	if selection == null:
		return []
	var moving = _selected_cards()
	if moving == null or moving.size() != 1:
		return []
	var card = moving[0]
	var suit: int = card["suit"]
	if card["rank"] == foundations[suit].size() + 1:
		return [suit]
	return []

func _toggle_selection(next_selection: Dictionary):
	if _same_selection(selection, next_selection):
		selection = null
	else:
		selection = next_selection

func _same_selection(left, right) -> bool:
	if left == null or right == null or left["kind"] != right["kind"]:
		return false
	match left["kind"]:
		"waste":
			return true
		"foundation":
			return left["suit"] == right["suit"]
		"tableau":
			return left["pile"] == right["pile"] and left["card"] == right["card"]
	return false

func _shuffle(deck: Array):
	for index in range(deck.size() - 1, 0, -1):
		var swap_index := rng.randi_range(0, index)
		var tmp = deck[index]
		deck[index] = deck[swap_index]
		deck[swap_index] = tmp

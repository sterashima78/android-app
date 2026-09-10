extends SceneTree

const KlondikeModel = preload("res://klondike_model.gd")

var failures := 0

func _init():
	_test_initial_deal()
	_test_stock_recycle()
	_test_foundation_move()
	_test_tableau_move_and_auto_flip()
	_test_invalid_tableau_run()
	_test_win_detection()

	if failures == 0:
		print("Klondike model tests passed")
		quit(0)
		return

	push_error("Klondike model tests failed: %d" % failures)
	quit(1)

func _expect(condition: bool, message: String):
	if condition:
		return
	failures += 1
	push_error(message)

func _card(suit: int, rank: int) -> Dictionary:
	return {"suit": suit, "rank": rank}

func _face_up(card: Dictionary) -> Dictionary:
	return {"card": card, "face_up": true}

func _empty_tableau() -> Array:
	return [[], [], [], [], [], [], []]

func _reset_empty(game):
	game.stock = []
	game.waste = []
	game.foundations = [[], [], [], []]
	game.tableau = _empty_tableau()
	game.selection = null
	game.moves = 0

func _test_initial_deal():
	var game = KlondikeModel.new()
	game.reset(1)

	_expect(game.tableau.size() == 7, "initial tableau must contain seven piles")
	_expect(game.stock.size() == 24, "initial stock must contain 24 cards")
	_expect(game.waste.is_empty(), "initial waste must be empty")

	var keys := {}
	for card in game.stock:
		keys["%d-%d" % [card["suit"], card["rank"]]] = true
	for pile_index in range(game.tableau.size()):
		var pile: Array = game.tableau[pile_index]
		_expect(pile.size() == pile_index + 1, "tableau pile %d has wrong size" % pile_index)
		for card_index in range(pile.size()):
			var tableau_card: Dictionary = pile[card_index]
			keys["%d-%d" % [tableau_card["card"]["suit"], tableau_card["card"]["rank"]]] = true
			_expect(
				tableau_card["face_up"] == (card_index == pile_index),
				"only the top card of each initial tableau pile may be face up",
			)
	_expect(keys.size() == 52, "initial deal must contain 52 unique cards")

func _test_stock_recycle():
	var game = KlondikeModel.new()
	game.reset(1)
	for _draw in range(24):
		game.draw_stock()

	_expect(game.stock.is_empty(), "stock must be empty after 24 draws")
	_expect(game.waste.size() == 24, "waste must contain all drawn cards")
	var first_drawn: Dictionary = game.waste[0]

	game.draw_stock()

	_expect(game.stock.size() == 24, "recycle must restore all waste cards to stock")
	_expect(game.waste.is_empty(), "recycle must clear waste")
	_expect(game.stock.back()["suit"] == first_drawn["suit"], "recycle must preserve draw order suit")
	_expect(game.stock.back()["rank"] == first_drawn["rank"], "recycle must preserve draw order rank")

func _test_foundation_move():
	var game = KlondikeModel.new()
	_reset_empty(game)
	var ace := _card(KlondikeModel.Suit.HEARTS, 1)
	game.waste = [ace]
	game.selection = {"kind": "waste"}

	_expect(
		game.valid_foundation_targets() == [KlondikeModel.Suit.HEARTS],
		"heart ace must target the empty heart foundation",
	)
	game.move_selected_to_foundation(KlondikeModel.Suit.HEARTS)

	_expect(game.waste.is_empty(), "foundation move must remove the waste card")
	_expect(game.foundations[KlondikeModel.Suit.HEARTS].size() == 1, "foundation move must append the ace")
	_expect(game.moves == 1, "foundation move must increment moves")

func _test_tableau_move_and_auto_flip():
	var game = KlondikeModel.new()
	_reset_empty(game)
	var hidden_nine := _card(KlondikeModel.Suit.SPADES, 9)
	var red_seven := _card(KlondikeModel.Suit.HEARTS, 7)
	var black_eight := _card(KlondikeModel.Suit.CLUBS, 8)
	game.tableau[0] = [
		{"card": hidden_nine, "face_up": false},
		_face_up(red_seven),
	]
	game.tableau[1] = [_face_up(black_eight)]
	game.selection = {"kind": "tableau", "pile": 0, "card": 1}

	_expect(game.valid_tableau_targets() == [1], "red seven must target black eight")
	game.move_selected_to_tableau(1)

	_expect(game.tableau[0].size() == 1, "source tableau must remove the moved card")
	_expect(game.tableau[0][0]["face_up"], "exposed source card must flip face up automatically")
	_expect(game.tableau[1].size() == 2, "target tableau must receive the moved card")
	_expect(game.tableau[1].back()["card"]["rank"] == 7, "target tableau must end with the moved rank")
	_expect(game.moves == 1, "tableau move must increment moves")

func _test_invalid_tableau_run():
	var game = KlondikeModel.new()
	_reset_empty(game)
	var black_seven := _card(KlondikeModel.Suit.SPADES, 7)
	var red_six := _card(KlondikeModel.Suit.HEARTS, 6)
	var red_five := _card(KlondikeModel.Suit.DIAMONDS, 5)
	game.tableau[0] = [_face_up(black_seven)]
	game.tableau[1] = [_face_up(red_six), _face_up(red_five)]
	game.selection = {"kind": "tableau", "pile": 1, "card": 0}

	_expect(game.valid_tableau_targets().is_empty(), "same-color descending run must have no tableau target")
	game.move_selected_to_tableau(0)
	_expect(game.tableau[0].size() == 1, "invalid run must not modify target tableau")
	_expect(game.tableau[1].size() == 2, "invalid run must not modify source tableau")
	_expect(game.moves == 0, "invalid run must not increment moves")

func _test_win_detection():
	var game = KlondikeModel.new()
	_reset_empty(game)
	for suit in KlondikeModel.SUITS:
		for rank in range(1, 14):
			game.foundations[suit].append(_card(suit, rank))
	_expect(game.is_won(), "all 52 foundation cards must complete the game")

extends SceneTree

const KlondikeScreen = preload("res://klondike.gd")

var failures := 0

func _init():
	_test_identity_mapping()
	_test_scaled_and_translated_mapping()
	_test_empty_safe_area_falls_back()

	if failures == 0:
		print("Klondike layout tests passed")
		quit(0)
		return

	push_error("Klondike layout tests failed: %d" % failures)
	quit(1)

func _expect(condition: bool, message: String):
	if condition:
		return
	failures += 1
	push_error(message)

func _expect_vector(actual: Vector2, expected: Vector2, message: String):
	_expect(actual.is_equal_approx(expected), "%s: actual=%s expected=%s" % [message, actual, expected])

func _test_identity_mapping():
	var mapped := KlondikeScreen.safe_content_rect(
		Vector2(1920, 1080),
		Rect2i(64, 48, 1792, 984),
		Transform2D.IDENTITY,
	)
	_expect_vector(mapped.position, Vector2(64, 48), "identity safe-area position")
	_expect_vector(mapped.size, Vector2(1792, 984), "identity safe-area size")

func _test_scaled_and_translated_mapping():
	var screen_transform := Transform2D.IDENTITY
	screen_transform.x *= 0.5
	screen_transform.y *= 0.5
	screen_transform.origin = Vector2(20, 10)
	var mapped := KlondikeScreen.safe_content_rect(
		Vector2(1920, 1080),
		Rect2i(60, 35, 900, 500),
		screen_transform,
	)
	_expect_vector(mapped.position, Vector2(80, 50), "scaled safe-area position")
	_expect_vector(mapped.size, Vector2(1800, 1000), "scaled safe-area size")

func _test_empty_safe_area_falls_back():
	var mapped := KlondikeScreen.safe_content_rect(
		Vector2(1920, 1080),
		Rect2i(),
		Transform2D.IDENTITY,
	)
	_expect_vector(mapped.position, Vector2.ZERO, "empty safe-area fallback position")
	_expect_vector(mapped.size, Vector2(1920, 1080), "empty safe-area fallback size")

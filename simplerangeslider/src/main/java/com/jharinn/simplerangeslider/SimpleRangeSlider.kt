package com.jharinn.simplerangeslider

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.withStyledAttributes


public class SimpleRangeSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRef: Int = 0,
) : View(context, attrs, defStyleAttr, defStyleRef) {

    // 기본 속성값들
    private var thumbColor = 0
    private var trackColorActive = 0
    private var trackColorInactive = 0
    private var thumbRadius = 10
    private var trackHeight = 48
    private var trackPadding = 10
    private var minValue = 0
    private var maxValue = 100
    private var defaultMinValue = 0
    private var defaultMaxValue = 100
    // 화면위치 계산에 사용하는 정규화된 값
    private var normalizedMinValue: Float = 0.0F
    private var normalizedMaxValue: Float = 1.0F
    // 현재 선택된 Thumb
    private var pressedThumb: Thumb? = null

    private var isDragging = false
    private var scaledTouchSlop: Int = 0
    private var previousTouchedX = 0.0F

    private val mRect: RectF
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var blurFilter: BlurMaskFilter

    private var listener: OnValueChangeListener? = null

    private enum class Thumb {
        MIN, MAX
    }

    init {
        context.withStyledAttributes(attrs, R.styleable.SrsSimpleRangeSlider) {
            thumbColor = getColor(R.styleable.SrsSimpleRangeSlider_srs_thumbColor, Color.BLUE)
            minValue = getInt(R.styleable.SrsSimpleRangeSlider_srs_minValue, defaultMinValue)
            maxValue = getInt(R.styleable.SrsSimpleRangeSlider_srs_maxValue, defaultMaxValue)
            defaultMinValue = getInt(R.styleable.SrsSimpleRangeSlider_srs_minValue, defaultMinValue)
            defaultMaxValue = getInt(R.styleable.SrsSimpleRangeSlider_srs_maxValue, defaultMaxValue)
            trackColorActive = getColor(R.styleable.SrsSimpleRangeSlider_srs_trackColorActive, Color.GREEN)
            trackColorInactive = getColor(R.styleable.SrsSimpleRangeSlider_srs_trackColorInactive, Color.WHITE)
            trackHeight = getDimensionPixelSize(R.styleable.SrsSimpleRangeSlider_srs_trackHeight, 5)
            thumbRadius = getDimensionPixelSize(R.styleable.SrsSimpleRangeSlider_srs_thumbRadius, 20)
            trackPadding = getDimensionPixelSize(R.styleable.SrsSimpleRangeSlider_srs_trackPadding, 30) + thumbRadius
        }

        mRect = RectF(
            0F, 0F, 0F, 0F
        )
        scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
        blurFilter = BlurMaskFilter(thumbRadius.toFloat(), BlurMaskFilter.Blur.OUTER)
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // background track 그리기
        paint.color = trackColorInactive
        mRect.top = height / 2 + trackPadding.toFloat() - trackHeight
        mRect.bottom = height / 2 + trackPadding.toFloat() + trackHeight
        mRect.left = trackPadding.toFloat()
        mRect.right = (width - trackPadding).toFloat()
        canvas.drawRect(mRect, paint)

        // active track 그리기
        mRect.left = normalizedToScreen(normalizedMinValue)
        mRect.right = normalizedToScreen(normalizedMaxValue)

        // 초기값일 경우 track color 설정
        val isDefaultValue: Boolean = (getSelectedMinValue() == defaultMinValue.toFloat() && getSelectedMaxValue() == defaultMaxValue.toFloat())
        if(isDefaultValue)
            paint.color = trackColorInactive
        else
            paint.color = trackColorActive
        canvas.drawRect(mRect, paint)

        // 왼쪽 thumb 그리기
        paint.color = thumbColor
        canvas.drawCircle(
            Math.max(normalizedToScreen(normalizedMinValue), trackPadding.toFloat()),
            height / 2 + trackPadding.toFloat(),
            thumbRadius.toFloat(), paint
        )

        // 오른쪽 thumb 그리기
        canvas.drawCircle(
            normalizedToScreen(normalizedMaxValue),
            height / 2 + trackPadding.toFloat(),
            thumbRadius.toFloat(), paint
        )

        // 그림자 추가
        paint.apply {
            color = Color.LTGRAY;
            strokeWidth = thumbRadius.toFloat();
            isAntiAlias = true;
            maskFilter = blurFilter
        }

        canvas.drawCircle(
            Math.max(normalizedToScreen(normalizedMinValue), trackPadding.toFloat()),
            height / 2 + trackPadding.toFloat(),
            thumbRadius.toFloat(), paint
        )

        canvas.drawCircle(
            normalizedToScreen(normalizedMaxValue),
            height / 2 + trackPadding.toFloat(),
            thumbRadius.toFloat(), paint
        )

        // Painter를 재사용하기 때문에, 블러 필터 사용 후 null처리
        paint.apply {
            maskFilter = null
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }

        val x = event.x

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousTouchedX = x
                pressedThumb = evalPressedThumb(x)

                if (pressedThumb == null)
                    return super.onTouchEvent(event);

                isPressed = true
                invalidate()
                onStartTrackingTouch()
                trackTouchEvent(x)
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedThumb==null)
                    return super.onTouchEvent(event);

                if(isDragging) {
                    trackTouchEvent(x)
                }
                else {
                    val x = event.x

                    if (Math.abs(x - previousTouchedX) > scaledTouchSlop) {
                        isPressed = true
                        invalidate()
                        onStartTrackingTouch()
                        trackTouchEvent(x)
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                listener?.onValueChanged(
                    this,
                    getSelectedMinValue(),
                    getSelectedMaxValue()
                )
            }
            MotionEvent.ACTION_UP -> {
                if (pressedThumb==null)
                    return super.onTouchEvent(event);

                if (isDragging) {
                    trackTouchEvent(x)
                    onStopTrackingTouch()
                    isPressed = false
                } else {
                    onStartTrackingTouch()
                    trackTouchEvent(x)
                    onStopTrackingTouch()
                }

                pressedThumb = null
                invalidate()

                listener?.onValueChanged(
                    this,
                    getSelectedMinValue(),
                    getSelectedMaxValue()
                )
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    onStopTrackingTouch()
                    isPressed = false
                }
                invalidate()
            }
        }
        return true
    }

    private fun onStartTrackingTouch() {
        isDragging = true
    }

    private fun onStopTrackingTouch() {
        isDragging = false
    }

    private fun trackTouchEvent(x: Float) {
        if (Thumb.MIN == pressedThumb)
            setNormalizedMinValue(screenToNormalized(x))
        else
            setNormalizedMaxValue(screenToNormalized(x))
    }

    private fun evalPressedThumb(touchX: Float): Thumb? {
        val minThumbPressed = isInThumbRange(touchX, normalizedMinValue)
        val maxThumbPressed = isInThumbRange(touchX, normalizedMaxValue)

        return when {
            // 도형이 겹쳤을 경우, 빈 공간이 더 여유 있는 쪽으로 이동
            minThumbPressed && maxThumbPressed -> if (touchX / width > 0.5f) Thumb.MIN else Thumb.MAX
            minThumbPressed -> Thumb.MIN
            maxThumbPressed -> Thumb.MAX
            else -> null
        }
    }

    private fun isInThumbRange(touchX: Float, normalizedThumbValue: Float): Boolean {
        val diff = Math.abs(touchX - normalizedToScreen(normalizedThumbValue))

        return diff <= thumbRadius + thumbRadius/2
    }

    /**
     * min value & 왼쪽 thumb 위치 업데이트
     */
    private fun setNormalizedMinValue(value: Float) {
        normalizedMinValue =  Math.max(0.0F, Math.min(1.0F, Math.min(value, normalizedMaxValue)))
        invalidate()
    }

    /**
     * max value & 오른쪽 thumb 위치 업데이트
     */
    private fun setNormalizedMaxValue(value: Float) {
        normalizedMaxValue = Math.max(0.0F, Math.min(1.0F, Math.max(value, normalizedMinValue)))
        invalidate()
    }

    /**
     * 정규화<->값 변환 메소드
     */
    private fun normalizedToValue(normalized: Float): Float = minValue + normalized * (maxValue - minValue)
    private fun normalizedToScreen(normalizedValue: Float): Float = trackPadding + normalizedValue * (width - trackPadding * 2)
    private fun screenToNormalized(x: Float): Float = if (width <= 2 * trackPadding) 0F else {
        val result = (x - trackPadding) / (width - 2 * trackPadding)
        Math.min(1F, Math.max(0F, result))
    }

    public fun getSelectedMinValue(): Float {
        return normalizedToValue(normalizedMinValue)
    }

    public fun getSelectedMaxValue(): Float {
        return normalizedToValue(normalizedMaxValue)
    }

    public fun setOnRangeSeekBarChangeListener(listener: OnValueChangeListener) {
        this.listener = listener
    }

    public interface OnValueChangeListener {
        public fun onValueChanged(bar: SimpleRangeSlider, minValue: Float, maxValue: Float)
    }
    
    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        bundle.putParcelable("SUPER", super.onSaveInstanceState())
        bundle.putDouble("MIN", normalizedMinValue.toDouble())
        bundle.putDouble("MAX", normalizedMaxValue.toDouble())
        return bundle
    }

    override fun onRestoreInstanceState(parcel: Parcelable) {
        val bundle = parcel as Bundle
        super.onRestoreInstanceState(bundle.getParcelable("SUPER"))
        normalizedMinValue = bundle.getDouble("MIN").toFloat()
        normalizedMaxValue = bundle.getDouble("MAX").toFloat()
    }
}
package co.edu.unal.tictactoe_reto1;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class BoardView extends View {

    public static final int GRID_WIDTH = 6;

    private Bitmap mHumanBitmap;
    private Bitmap mComputerBitmap;

    private Paint mPaint;
    private TicTacToeGame mGame;
    private Rect mDestRect;


    private void initialize() {

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mHumanBitmap = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.x_img
        );

        mComputerBitmap = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.o_img
        );

        mDestRect = new Rect();
    }

    public int getBoardCellWidth() {
        return getWidth() / 3;
    }

    public int getBoardCellHeight() {
        return getHeight() / 3;
    }

    public void setGame(TicTacToeGame game) {
        mGame = game;
    }

    public BoardView(Context context) {
        super(context);
        initialize();
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public BoardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        int boardWidth = getWidth();
        int boardHeight = getHeight();

        mPaint.setColor(Color.LTGRAY);
        mPaint.setStrokeWidth(GRID_WIDTH);

        int cellWidth = boardWidth / 3;
        int cellHeight = boardHeight / 3;

        // Líneas verticales
        canvas.drawLine(
                cellWidth,
                0,
                cellWidth,
                boardHeight,
                mPaint
        );

        canvas.drawLine(
                cellWidth * 2,
                0,
                cellWidth * 2,
                boardHeight,
                mPaint
        );

        // Líneas horizontales
        canvas.drawLine(
                0,
                cellHeight,
                boardWidth,
                cellHeight,
                mPaint
        );

        canvas.drawLine(
                0,
                cellHeight * 2,
                boardWidth,
                cellHeight * 2,
                mPaint
        );

        // Dibujar X y O
        for (int i = 0; i < TicTacToeGame.BOARD_SIZE; i++) {

            char occupant = mGame.getBoardOccupant(i);

            int row = i / 3;
            int col = i % 3;

            int left = col * cellWidth;
            int top = row * cellHeight;

            mDestRect.set(
                    left,
                    top,
                    left + cellWidth,
                    top + cellHeight
            );

            if (occupant == TicTacToeGame.HUMAN_PLAYER) {

                canvas.drawBitmap(
                        mHumanBitmap,
                        null,
                        mDestRect,
                        mPaint
                );

            } else if (occupant == TicTacToeGame.COMPUTER_PLAYER) {

                canvas.drawBitmap(
                        mComputerBitmap,
                        null,
                        mDestRect,
                        mPaint
                );
            }
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
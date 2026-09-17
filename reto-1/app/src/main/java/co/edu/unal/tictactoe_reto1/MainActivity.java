package co.edu.unal.tictactoe_reto1;


import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {


    // Botones del tablero
    private Button[] mBoardButtons;


    // Texto inferior
    private TextView mInfoTextView;

    private Button mNewGameButton;


    // Lógica del juego
    private TicTacToeGame mGame;


    // Indica si la partida terminó
    private boolean mGameOver;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);



        // Crear botones
        mBoardButtons = new Button[TicTacToeGame.BOARD_SIZE];



        mBoardButtons[0] = findViewById(R.id.one);
        mBoardButtons[1] = findViewById(R.id.two);
        mBoardButtons[2] = findViewById(R.id.three);

        mBoardButtons[3] = findViewById(R.id.four);
        mBoardButtons[4] = findViewById(R.id.five);
        mBoardButtons[5] = findViewById(R.id.six);

        mBoardButtons[6] = findViewById(R.id.seven);
        mBoardButtons[7] = findViewById(R.id.eight);
        mBoardButtons[8] = findViewById(R.id.nine);



        mInfoTextView = findViewById(R.id.information);

        mNewGameButton = findViewById(R.id.new_game);



        // Crear juego
        mGame = new TicTacToeGame();

        mNewGameButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                startNewGame();

            }

        });



        startNewGame();

    }





    private void startNewGame(){


        mGame.clearBoard();


        mGameOver = false;



        for(int i = 0; i < mBoardButtons.length; i++){


            mBoardButtons[i].setText("");

            mBoardButtons[i].setEnabled(true);


            mBoardButtons[i]
                    .setOnClickListener(new ButtonClickListener(i));


        }


        mInfoTextView.setText("Tu turno");


    }





    private class ButtonClickListener implements View.OnClickListener{


        private int location;



        public ButtonClickListener(int location){

            this.location = location;

        }





        @Override
        public void onClick(View view){



            if(!mGameOver && mBoardButtons[location].isEnabled()){



                // Movimiento humano
                setMove(
                        TicTacToeGame.HUMAN_PLAYER,
                        location
                );



                int winner = mGame.checkForWinner();



                if(winner != 0){

                    showWinner(winner);

                    return;

                }




                // Movimiento computador
                mInfoTextView.setText("Turno de Android");



                int computerMove = mGame.getComputerMove();



                setMove(
                        TicTacToeGame.COMPUTER_PLAYER,
                        computerMove
                );



                winner = mGame.checkForWinner();



                if(winner != 0){

                    showWinner(winner);

                }
                else{

                    mInfoTextView.setText("Tu turno");

                }



            }


        }


    }







    private void setMove(char player, int location){



        mGame.setMove(player, location);



        mBoardButtons[location].setEnabled(false);



        mBoardButtons[location]
                .setText(String.valueOf(player));



        if(player == TicTacToeGame.HUMAN_PLAYER){


            mBoardButtons[location]
                    .setTextColor(Color.GREEN);


        }
        else{


            mBoardButtons[location]
                    .setTextColor(Color.RED);


        }



    }








    private void showWinner(int winner){



        mGameOver = true;



        if(winner == 1){

            mInfoTextView.setText("Empate!");

        }

        else if(winner == 2){

            mInfoTextView.setText("Ganaste!");

        }

        else if(winner == 3){

            mInfoTextView.setText("Android ganó!");

        }




        // Deshabilitar tablero

        for(Button button : mBoardButtons){

            button.setEnabled(false);

        }



    }



}
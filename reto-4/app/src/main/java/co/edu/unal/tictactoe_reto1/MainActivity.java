package co.edu.unal.tictactoe_reto1;


import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.widget.Toast;

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.options_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.new_game_menu) {

            startNewGame();
            return true;

        } else if (id == R.id.ai_difficulty) {

            showDifficultyDialog();
            return true;

        } else if (id == R.id.quit) {

            showQuitDialog();
            return true;
        }

        return false;
    }

    private void showDifficultyDialog() {

        final String[] levels = {
                "Easy",
                "Harder",
                "Expert"
        };


        int selected = 2;

        TicTacToeGame.DifficultyLevel currentLevel =
                mGame.getDifficultyLevel();


        if (currentLevel == TicTacToeGame.DifficultyLevel.Easy) {

            selected = 0;

        } else if (currentLevel == TicTacToeGame.DifficultyLevel.Harder) {

            selected = 1;

        }


        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);


        builder.setTitle("Choose difficulty");


        builder.setSingleChoiceItems(
                levels,
                selected,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(
                            DialogInterface dialog,
                            int which) {


                        if (which == 0) {

                            mGame.setDifficultyLevel(
                                    TicTacToeGame.DifficultyLevel.Easy
                            );

                        } else if (which == 1) {

                            mGame.setDifficultyLevel(
                                    TicTacToeGame.DifficultyLevel.Harder
                            );

                        } else {

                            mGame.setDifficultyLevel(
                                    TicTacToeGame.DifficultyLevel.Expert
                            );

                        }


                        dialog.dismiss();


                        Toast.makeText(
                                MainActivity.this,
                                "Difficulty: " + levels[which],
                                Toast.LENGTH_SHORT
                        ).show();

                    }
                });


        builder.show();
    }

    private void showQuitDialog() {

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);

        builder.setTitle("Quit");

        builder.setMessage("Are you sure you want to quit?");

        builder.setPositiveButton(
                "Yes",
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(
                            DialogInterface dialog,
                            int which) {

                        finish();

                    }
                });

        builder.setNegativeButton(
                "No",
                null);

        builder.show();
    }


}
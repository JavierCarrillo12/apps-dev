package co.edu.unal.tictactoe_reto1;


import java.util.Random;



public class TicTacToeGame {



    public static final char HUMAN_PLAYER = 'X';

    public static final char COMPUTER_PLAYER = 'O';

    public static final char OPEN_SPOT = ' ';



    public static final int BOARD_SIZE = 9;



    private char[] board;



    private Random random;





    public TicTacToeGame(){


        board = new char[BOARD_SIZE];


        random = new Random();


        clearBoard();


    }






    public void clearBoard(){


        for(int i = 0; i < BOARD_SIZE; i++){


            board[i] = OPEN_SPOT;


        }


    }







    public void setMove(char player, int location){



        if(board[location] == OPEN_SPOT){


            board[location] = player;


        }



    }








    public int getComputerMove(){



        int move;



        do{


            move = random.nextInt(BOARD_SIZE);



        }while(board[move] != OPEN_SPOT);




        return move;



    }









    public int checkForWinner(){



        int[][] win = {



                {0,1,2},

                {3,4,5},

                {6,7,8},



                {0,3,6},

                {1,4,7},

                {2,5,8},



                {0,4,8},

                {2,4,6}



        };






        for(int i = 0; i < win.length; i++){



            int a = win[i][0];

            int b = win[i][1];

            int c = win[i][2];




            if(board[a] != OPEN_SPOT &&

                    board[a] == board[b] &&

                    board[a] == board[c]){


                if(board[a] == HUMAN_PLAYER){

                    return 2;

                }

                else{

                    return 3;

                }


            }



        }







        // Revisar empate

        for(int i = 0; i < BOARD_SIZE; i++){


            if(board[i] == OPEN_SPOT){


                return 0;


            }


        }




        return 1;



    }



}